package com.windrr.hyundae.speech

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import com.windrr.hyundae.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

enum class SpeechInputStatus { IDLE, PREPARING, READY, LISTENING, ERROR }

data class SpeechInputState(
    val status: SpeechInputStatus = SpeechInputStatus.IDLE,
    val partialText: String = "",
    val finalText: String = "",
    val finalResultCount: Int = 0,
    val errorMessage: String? = null,
)

class SpeechInputController(context: Context, private val mode: Mode) {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(SpeechInputState())
    val state: StateFlow<SpeechInputState> = _state.asStateFlow()

    /** 마이크 소리 크기 (SDK onUpdatedRms 원본 값). Voice orb 반응에 쓴다 */
    private val _rms = MutableStateFlow(0.0)
    val rms: StateFlow<Double> = _rms.asStateFlow()

    private var speechToText: SpeechToText? = null
    private var isReady = false
    private var startWhenReady = false

    private val resultListener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            _state.update {
                if (isFinal) {
                    it.copy(partialText = "", finalText = text, finalResultCount = it.finalResultCount + 1)
                } else {
                    it.copy(partialText = text)
                }
            }
        }

        override fun onUpdatedEpdData(startTime: Long, endTime: Long) = Unit

        override fun onStartedRecognition() {
            _state.update { it.copy(status = SpeechInputStatus.LISTENING, partialText = "", errorMessage = null) }
        }

        override fun onEndedRecognition() {
            _rms.value = 0.0
            _state.update { it.copy(status = SpeechInputStatus.READY, partialText = "") }
        }

        override fun onError(errorType: ErrorType) {
            fail("인식 오류: ${errorType.name}")
        }

        // 엔진이 녹음을 시작함 → 말을 기다리는 중이므로 "듣는 중"
        override fun onReady() {
            _state.update { if (it.status == SpeechInputStatus.ERROR) it else it.copy(status = SpeechInputStatus.LISTENING, errorMessage = null) }
        }

        override fun onUpdatedRms(rms: Double) {
            _rms.value = rms
        }
    }

    fun start() {
        val engine = speechToText
        if (engine == null || !isReady) {
            startWhenReady = true
            prepare()
            return
        }
        _state.update { it.copy(errorMessage = null) }
        engine.request()
    }

    fun stop() {
        startWhenReady = false
        speechToText?.stop()
    }

    fun release() {
        startWhenReady = false
        releaseEngine()
        _state.update { it.copy(status = SpeechInputStatus.IDLE, partialText = "") }
    }

    private fun releaseEngine() {
        isReady = false
        speechToText?.let {
            it.removeListener(resultListener)
            it.release()
        }
        speechToText = null
    }

    private fun prepare() {
        if (speechToText != null) return
        _state.update { it.copy(status = SpeechInputStatus.PREPARING, errorMessage = null) }
        val engine = SpeechToText(appContext, mode)
        speechToText = engine
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                engine.setLocale(Locale.KOREA) // ko_KR. "ko"만 주면 차량 온디바이스 엔진이 unsupported locale로 실패 (에뮬레이터 확인)
                engine.addListener(resultListener)
                if (mode == Mode.ON_DEVICE) onPrepared() else register(engine)
            }

            override fun onFailure(errorType: ErrorType) {
                fail("초기화 실패: ${errorType.name}")
            }
        })
    }

    private fun register(engine: SpeechToText) {
        val clientId = BuildConfig.PLEOS_CLIENT_ID
        val clientSecret = BuildConfig.PLEOS_CLIENT_SECRET
        if (clientId.isBlank() || clientSecret.isBlank()) {
            fail("local.properties에 PLEOS_CLIENT_ID/PLEOS_CLIENT_SECRET이 없습니다")
            return
        }
        engine.registerApp(clientId, clientSecret, object : OnServerConnectionListener {
            override fun onConnected() = onPrepared()

            override fun onFailed(errorType: ErrorType) {
                fail("서버 연결 실패: ${errorType.name}")
            }
        })
    }

    private fun onPrepared() {
        isReady = true
        _state.update { it.copy(status = SpeechInputStatus.READY) }
        if (startWhenReady) {
            startWhenReady = false
            start()
        }
    }

    private fun fail(message: String) {
        startWhenReady = false
        if (!isReady) releaseEngine()
        _state.update { it.copy(status = SpeechInputStatus.ERROR, partialText = "", errorMessage = message) }
    }
}
