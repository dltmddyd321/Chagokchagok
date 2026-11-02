package com.windrr.hyundae.sdk

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.windrr.hyundae.BuildConfig
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener as SttConnectionListener
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener as TtsConnectionListener

/**
 * 서버 연결 SDK — local.properties의 PLEOS_CLIENT_ID/SECRET 필요.
 * 콘솔상 Coming Soon이라 registerApp 단계에서 UNAUTHENTICATED 등으로 실패할 수 있음.
 */
@RunWith(AndroidJUnit4::class)
class SpeechSdkTest {

    private fun requireCredentials() {
        assumeTrue(
            "local.properties에 PLEOS_CLIENT_ID/PLEOS_CLIENT_SECRET 없음",
            BuildConfig.PLEOS_CLIENT_ID.isNotBlank() && BuildConfig.PLEOS_CLIENT_SECRET.isNotBlank(),
        )
    }

    /** 서버·자격 증명 없이 차량 내장 엔진으로 합성 */
    @Test
    fun textToSpeech_onDevice_speak() = speak(TtsMode.ON_DEVICE)

    /** 2026-11 기준 PERMISSION_DENIED(엔드포인트 권한 미승인) — 콘솔에서 TTS 승인되면 통과해야 함 */
    @Test
    fun textToSpeech_server_speak() {
        requireCredentials()
        speak(TtsMode.SERVER)
    }

    private fun speak(mode: TtsMode) {
        lateinit var tts: TextToSpeech
        try {
            awaitCallback<Unit>("TTS($mode).initialize") { ok, err ->
                onMain {
                    tts = TextToSpeech(targetContext, mode)
                    tts.initialize(object : TtsInitListener {
                        override fun onSuccess() = ok(Unit)
                        override fun onFailure(errorType: TtsErrorType) = err(errorType.toString())
                    })
                }
            }
            if (mode != TtsMode.ON_DEVICE) {
                awaitCallback<Unit>("TTS($mode).registerApp") { ok, err ->
                    tts.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET, object : TtsConnectionListener {
                        override fun onConnected() = ok(Unit)
                        override fun onFailed(errorType: TtsErrorType) = err(errorType.toString())
                    })
                }
            }
            // 재생 완료(onDone)까지 확인. 서버 오류는 onStart 이후에 올 수 있어 onStart로는 판정 불가
            awaitCallback<Unit>("TTS($mode).speak", timeoutSec = 20) { ok, err ->
                tts.addEventListener(object : EventListener {
                    override fun onStart() = Unit
                    override fun onReady() = Unit
                    override fun onDone() = ok(Unit)
                    override fun onError(errorType: TtsErrorType) = err(errorType.toString())
                    override fun onStop() = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                })
                tts.speak("플레오스 음성 합성 테스트입니다.", true) // 두 번째 인자 = isFinal(스트리밍 텍스트 끝)
            }
        } finally {
            onMain { runCatching { tts.release() } }
        }
    }

    /** 에뮬레이터 마이크 입력 없이 초기화·서버 등록까지만 확인 */
    @Test
    fun speechToText_registerApp() {
        requireCredentials()
        lateinit var stt: SpeechToText
        try {
            awaitCallback<Unit>("STT.initialize") { ok, err ->
                onMain {
                    stt = SpeechToText(targetContext, SttMode.SERVER)
                    stt.initialize(object : SttInitListener {
                        override fun onSuccess() = ok(Unit)
                        override fun onFailure(errorType: ai.pleos.playground.stt.constant.ErrorType) = err(errorType.toString())
                    })
                }
            }
            awaitCallback<Unit>("STT.registerApp") { ok, err ->
                stt.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET, object : SttConnectionListener {
                    override fun onConnected() = ok(Unit)
                    override fun onFailed(errorType: ai.pleos.playground.stt.constant.ErrorType) = err(errorType.toString())
                })
            }
        } finally {
            onMain { runCatching { stt.release() } }
        }
    }
}
