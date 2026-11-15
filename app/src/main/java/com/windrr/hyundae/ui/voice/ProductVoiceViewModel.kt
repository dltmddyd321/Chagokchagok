package com.windrr.hyundae.ui.voice

import ai.pleos.playground.stt.constant.Mode
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.windrr.hyundae.speech.SpeechInputController
import com.windrr.hyundae.speech.SpeechInputStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 제품명 말하기: 열리면 바로 듣기 시작 → 소리 크기를 orb로 → 말이 끝나고 침묵이 이어지면 인식 완료.
 * 침묵 판단: 인식된 글자가 있고, [SILENCE_MS] 동안 새 글자도 없고 소리도 작으면 끝.
 * (SDK 자체 끝점 감지로 최종 결과가 먼저 오면 그걸로 끝)
 */
class ProductVoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = SpeechInputController(application, Mode.ON_DEVICE)

    private val _state = MutableStateFlow(ProductVoiceUiState())
    val state: StateFlow<ProductVoiceUiState> = _state.asStateFlow()

    private val _effect = Channel<ProductVoiceEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var lastVoiceAtMs = 0L
    private var silenceJob: Job? = null
    private var finished = false

    /** 시작 시점의 최종 결과 수. 이전 세션 결과로 바로 끝나지 않도록 이보다 커졌을 때만 새 결과로 본다 */
    private var finalBaseline = 0

    // RMS 범위가 기기마다 달라서 지금까지 본 최소·최대로 정규화
    private var rmsFloor = Double.MAX_VALUE
    private var rmsCeil = Double.MIN_VALUE

    init {
        viewModelScope.launch {
            controller.state.collect { s ->
                when (s.status) {
                    SpeechInputStatus.PREPARING, SpeechInputStatus.IDLE ->
                        if (!finished) _state.update { it.copy(phase = VoicePhase.PREPARING) }
                    SpeechInputStatus.LISTENING -> _state.update { it.copy(phase = VoicePhase.LISTENING, errorMessage = null) }
                    SpeechInputStatus.ERROR -> _state.update { it.copy(phase = VoicePhase.ERROR, errorMessage = s.errorMessage, level = 0f) }
                    SpeechInputStatus.READY -> Unit
                }
                val hasNewFinal = s.finalResultCount > finalBaseline
                val text = (s.finalText.takeIf { hasNewFinal && s.partialText.isEmpty() } ?: s.partialText).trim()
                if (text.isNotEmpty() && text != _state.value.transcript && !finished) {
                    lastVoiceAtMs = System.currentTimeMillis()
                    _state.update { it.copy(transcript = text) }
                }
                // SDK가 끝점을 잡아 최종 결과를 줬으면 바로 완료
                if (hasNewFinal && s.finalText.isNotBlank()) finish(s.finalText.trim())
            }
        }
        viewModelScope.launch {
            controller.rms.collect { rms -> onRms(rms) }
        }
    }

    fun onIntent(intent: ProductVoiceIntent) {
        when (intent) {
            ProductVoiceIntent.Start, ProductVoiceIntent.Retry -> start()
            ProductVoiceIntent.Cancel -> {
                finished = true
                silenceJob?.cancel()
                controller.stop()
                viewModelScope.launch { _effect.send(ProductVoiceEffect.Dismiss) }
            }
        }
    }

    private fun start() {
        finished = false
        finalBaseline = controller.state.value.finalResultCount
        _state.value = ProductVoiceUiState(phase = VoicePhase.PREPARING)
        lastVoiceAtMs = System.currentTimeMillis()
        controller.start()
        watchSilence()
    }

    private fun onRms(rms: Double) {
        if (rms == 0.0 && _state.value.phase != VoicePhase.LISTENING) return
        rmsFloor = minOf(rmsFloor, rms)
        rmsCeil = max(rmsCeil * CEIL_DECAY, rms) // 큰 소리 기준은 천천히 내려온다
        val span = (rmsCeil - rmsFloor).takeIf { it > 1e-6 } ?: return
        val level = ((rms - rmsFloor) / span).toFloat().coerceIn(0f, 1f)
        if (level > VOICE_LEVEL) lastVoiceAtMs = System.currentTimeMillis()
        _state.update { it.copy(level = level) }
    }

    private fun watchSilence() {
        silenceJob?.cancel()
        silenceJob = viewModelScope.launch {
            while (!finished) {
                delay(CHECK_INTERVAL_MS)
                val s = _state.value
                if (s.phase == VoicePhase.LISTENING && s.transcript.isNotEmpty() &&
                    System.currentTimeMillis() - lastVoiceAtMs >= SILENCE_MS
                ) {
                    finish(s.transcript)
                }
            }
        }
    }

    /** 디버그 빌드 전용: 말한 것처럼 글자를 차례로 보여 준 뒤 인식 완료 (마이크 없는 에뮬레이터 시연 녹화용) */
    fun debugSpeak(text: String) {
        if (!com.windrr.hyundae.BuildConfig.DEBUG || finished) return
        silenceJob?.cancel()
        controller.stop()
        viewModelScope.launch {
            val words = text.trim().split(" ")
            words.indices.forEach { i ->
                _state.update { it.copy(phase = VoicePhase.LISTENING, errorMessage = null, transcript = words.take(i + 1).joinToString(" "), level = if (i % 2 == 0) 0.75f else 0.45f) }
                kotlinx.coroutines.delay(450)
            }
            _state.update { it.copy(level = 0.1f) }
            kotlinx.coroutines.delay(900)
            finish(text.trim())
        }
    }

    private fun finish(text: String) {
        if (finished || text.isBlank()) return
        finished = true
        silenceJob?.cancel()
        controller.stop()
        _state.update { it.copy(phase = VoicePhase.SEARCHING, transcript = text, level = 0f) }
        viewModelScope.launch { _effect.send(ProductVoiceEffect.Recognized(text)) }
    }

    override fun onCleared() {
        silenceJob?.cancel()
        controller.release()
    }

    private companion object {
        /** 말이 끝났다고 볼 침묵 길이 */
        const val SILENCE_MS = 1_500L
        const val CHECK_INTERVAL_MS = 100L

        /** 이보다 크면 말하는 중으로 본다 (정규화 0~1) */
        const val VOICE_LEVEL = 0.35f
        const val CEIL_DECAY = 0.995
    }
}
