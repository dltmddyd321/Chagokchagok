package com.windrr.hyundae.ui.loadcheck

/** 이번에 실을 짐 하나 (cm) */
data class LoadItem(
    val key: String,
    val name: String,
    val widthCm: Int,
    val depthCm: Int,
    val heightCm: Int,
    val quantity: Int = 1,
    /** "조립 상태" 등 운반 상태 (제품 검색으로 추가했을 때만) */
    val stateLabel: String? = null,
) {
    val sizeLabel: String get() = "$widthCm × $depthCm × $heightCm cm"

    /** 카드 아래 줄: "1개 · 120 × 60 × 73 cm · 조립 상태" */
    val detailLabel: String get() = listOfNotNull("${quantity}개", sizeLabel, stateLabel).joinToString(" · ")
}

/**
 * 짐 정보 직접 입력 창 상태 (시안: 짐 이름 · 수량 + 가로·세로·높이 + 숫자판).
 * 치수는 입력 중 빈 칸을 허용하려고 문자열로 든다. 선택한 치수 항목에 숫자판 입력이 들어간다.
 * 이름은 비워 두면 "짐 N"으로 저장한다. 크기는 짐 1개 기준, 같은 크기는 수량으로.
 */
data class ManualInputState(
    val name: String = "",
    val quantity: Int = 1,
    val width: String = "",
    val depth: String = "",
    val height: String = "",
    val selected: ManualField = ManualField.WIDTH,
) {
    private fun String.cm(): Int? = toIntOrNull()?.takeIf { it in 1..LoadCheckUiState.MAX_CM }

    fun valueOf(field: ManualField): String = when (field) {
        ManualField.WIDTH -> width
        ManualField.DEPTH -> depth
        ManualField.HEIGHT -> height
    }

    fun with(field: ManualField, value: String): ManualInputState = when (field) {
        ManualField.WIDTH -> copy(width = value)
        ManualField.DEPTH -> copy(depth = value)
        ManualField.HEIGHT -> copy(height = value)
    }

    val canSave: Boolean get() = width.cm() != null && depth.cm() != null && height.cm() != null

    fun toItem(key: String, fallbackName: String): LoadItem? {
        if (!canSave) return null
        return LoadItem(key, name.trim().ifBlank { fallbackName }, width.cm()!!, depth.cm()!!, height.cm()!!, quantity)
    }
}

data class LoadCheckUiState(
    val items: List<LoadItem> = emptyList(),
    /** 동승자 수 (운전자 제외) */
    val companions: Int = 0,
    /** 치수 직접 입력 창이 열려 있으면 그 입력값 */
    val manualInput: ManualInputState? = null,
    /** 제품명 말하기 → 제품 규격 검색 상태 (null이면 창 닫힘) */
    val productSearch: ProductSearchState? = null,
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

enum class ManualField(val label: String) { WIDTH("가로"), DEPTH("세로"), HEIGHT("높이") }

sealed interface LoadCheckIntent {
    data object IncreaseCompanions : LoadCheckIntent

    data object DecreaseCompanions : LoadCheckIntent

    data class RemoveItem(val key: String) : LoadCheckIntent

    /** 치수 직접 입력 창 */
    data object OpenManualInput : LoadCheckIntent

    /** 음성으로 들은 제품명을 이름 칸에 채워 입력 창 열기 */
    data class OpenManualInputWithName(val name: String) : LoadCheckIntent

    /** 제품명 말하기 결과로 제품 규격 검색 (POST /products/search) */
    data class SearchProduct(val query: String) : LoadCheckIntent

    /** 후보가 여러 개(운반 상태별)일 때 고르기 */
    data class SelectProductCandidate(val index: Int) : LoadCheckIntent

    /** 이 짐 추가하기: 치수가 모두 있는 후보를 짐 목록에 */
    data object AddProductCandidate : LoadCheckIntent

    /** 치수가 빠진 후보·못 찾은 제품: 아는 값만 채워 직접 입력 창으로 */
    data object EnterProductDimensions : LoadCheckIntent

    /** 제품 규격 창 닫기 (다시 말하기 포함) */
    data object CloseProductSearch : LoadCheckIntent

    data object CloseManualInput : LoadCheckIntent

    /** 짐 이름 입력 */
    data class ManualNameChanged(val name: String) : LoadCheckIntent

    /** 수량 −/+ */
    data class StepManualQuantity(val delta: Int) : LoadCheckIntent

    /** 치수 항목 선택 (숫자판 입력 대상) */
    data class SelectField(val field: ManualField) : LoadCheckIntent

    /** 항목 옆 −/+ (1cm씩) */
    data class StepField(val field: ManualField, val delta: Int) : LoadCheckIntent

    /** 숫자판 숫자 */
    data class KeypadDigit(val digit: Int) : LoadCheckIntent

    /** 숫자판 지우기: 선택 항목을 비움 */
    data object KeypadClear : LoadCheckIntent

    /** 숫자판 ⌫: 마지막 숫자 지우기 */
    data object KeypadBackspace : LoadCheckIntent

    data object ConfirmManualInput : LoadCheckIntent

    /** 적재 가능 여부 확인 */
    data object Check : LoadCheckIntent
}

sealed interface LoadCheckEffect {
    /** 서버 배치 계산 요청에 넘길 값 */
    data class RequestCheck(val items: List<LoadItem>, val passengerCount: Int) : LoadCheckEffect
}
