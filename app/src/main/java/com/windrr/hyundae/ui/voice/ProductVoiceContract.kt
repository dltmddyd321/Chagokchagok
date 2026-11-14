package com.windrr.hyundae.ui.voice

enum class VoicePhase {
    /** 음성 인식 엔진 준비 중 */
    PREPARING,

    /** 듣는 중 */
    LISTENING,

    /** 말이 끝나 제품 규격을 찾는 중 (API 호출 자리) */
    SEARCHING,

    ERROR,
}

data class ProductVoiceUiState(
    val phase: VoicePhase = VoicePhase.PREPARING,
    /** 지금까지 인식한 말 (중간 결과 포함) */
    val transcript: String = "",
    /** 0~1 소리 크기 (orb 반응용) */
    val level: Float = 0f,
    val errorMessage: String? = null,
) {
    val statusText: String
        get() = when (phase) {
            VoicePhase.PREPARING -> "준비 중..."
            VoicePhase.LISTENING -> "듣는 중..."
            VoicePhase.SEARCHING -> "제품 규격을 찾는 중..."
            VoicePhase.ERROR -> errorMessage ?: "음성을 인식하지 못했어요"
        }
}

sealed interface ProductVoiceIntent {
    data object Start : ProductVoiceIntent

    data object Retry : ProductVoiceIntent

    data object Cancel : ProductVoiceIntent
}

sealed interface ProductVoiceEffect {
    /** 말이 끝나고 침묵이 이어졌을 때: 인식한 제품명 → 제품 규격 API 호출 */
    data class Recognized(val text: String) : ProductVoiceEffect

    data object Dismiss : ProductVoiceEffect
}
