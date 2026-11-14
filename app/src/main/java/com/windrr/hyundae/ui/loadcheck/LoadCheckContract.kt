package com.windrr.hyundae.ui.loadcheck

/** 이번에 실을 짐 하나 (cm) */
data class LoadItem(
    val key: String,
    val name: String,
    val widthCm: Int,
    val depthCm: Int,
    val heightCm: Int,
    val quantity: Int = 1,
) {
    val sizeLabel: String get() = "$widthCm × $depthCm × $heightCm cm"
}

/** 치수 직접 입력 창 상태. 숫자는 입력 중 빈 칸을 허용하려고 문자열로 든다 */
data class ManualInputState(
    val name: String = "",
    val width: String = "",
    val depth: String = "",
    val height: String = "",
    val quantity: Int = 1,
) {
    private fun String.cm(): Int? = toIntOrNull()?.takeIf { it in 1..LoadCheckUiState.MAX_CM }

    val canAdd: Boolean get() = name.isNotBlank() && width.cm() != null && depth.cm() != null && height.cm() != null

    fun toItem(key: String): LoadItem? {
        if (!canAdd) return null
        return LoadItem(key, name.trim(), width.cm()!!, depth.cm()!!, height.cm()!!, quantity)
    }
}

data class LoadCheckUiState(
    val items: List<LoadItem> = emptyList(),
    /** 동승자 수 (운전자 제외) */
    val companions: Int = 0,
    /** 치수 직접 입력 창이 열려 있으면 그 입력값 */
    val manualInput: ManualInputState? = null,
) {
    /** 운전자 포함 총 탑승 인원 */
    val totalPassengers: Int get() = 1 + companions

    val itemCount: Int get() = items.sumOf { it.quantity }

    val canAddItem: Boolean get() = itemCount < MAX_ITEMS

    val canCheck: Boolean get() = items.isNotEmpty()

    companion object {
        /** 시안: 최대 10개까지 추가 */
        const val MAX_ITEMS = 10

        /** 5인승: 운전자 외 최대 4명 */
        const val MAX_COMPANIONS = 4

        const val MAX_CM = 300
    }
}

enum class ManualField { NAME, WIDTH, DEPTH, HEIGHT }

sealed interface LoadCheckIntent {
    data object IncreaseCompanions : LoadCheckIntent

    data object DecreaseCompanions : LoadCheckIntent

    data class RemoveItem(val key: String) : LoadCheckIntent

    /** 치수 직접 입력 창 */
    data object OpenManualInput : LoadCheckIntent

    /** 음성으로 들은 제품명을 이름 칸에 채워 입력 창 열기 (제품 규격 API 연결 전 임시) */
    data class OpenManualInputWithName(val name: String) : LoadCheckIntent

    data object CloseManualInput : LoadCheckIntent

    data class ManualChanged(val field: ManualField, val value: String) : LoadCheckIntent

    data class ManualQuantity(val delta: Int) : LoadCheckIntent

    data object ConfirmManualInput : LoadCheckIntent

    /** 적재 가능 여부 확인 */
    data object Check : LoadCheckIntent
}

sealed interface LoadCheckEffect {
    /** 서버 배치 계산 요청에 넘길 값 */
    data class RequestCheck(val items: List<LoadItem>, val passengerCount: Int) : LoadCheckEffect
}
