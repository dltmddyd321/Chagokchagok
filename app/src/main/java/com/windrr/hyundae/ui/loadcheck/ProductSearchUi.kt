package com.windrr.hyundae.ui.loadcheck

import com.windrr.hyundae.data.remote.model.ProductCandidateResponse
import kotlin.math.roundToInt

/** "찾은 제품 규격" 창 상태 */
sealed interface ProductSearchState {
    val query: String

    /** 검색 중 (제품명 말하기 창이 "제품 규격을 찾는 중..."으로 떠 있음) */
    data class Searching(override val query: String) : ProductSearchState

    data class Found(
        override val query: String,
        val candidates: List<ProductCandidateUi>,
        val selectedIndex: Int = 0,
    ) : ProductSearchState {
        val selected: ProductCandidateUi get() = candidates[selectedIndex.coerceIn(candidates.indices)]
    }

    /** 결과 없음 · 검색 불가 · 통신 오류 */
    data class NotFound(override val query: String, val message: String) : ProductSearchState
}

/** 후보 하나 (화면용) */
data class ProductCandidateUi(
    val candidateId: String,
    /** 브랜드 칩. 서버 응답에 브랜드가 따로 없어서 지금은 IKEA 고정 */
    val brand: String,
    /** "락캅텐 / 아딜스 책상" */
    val name: String,
    /** "그레이/목재효과 블랙 · 895.840.27" (변형 · 모델). 없으면 null */
    val detail: String?,
    val transportState: TransportState,
    val widthCm: Double?,
    val depthCm: Double?,
    val heightCm: Double?,
    /** 출처 제목 (첫 번째) */
    val sourceTitle: String?,
) {
    /** 세 치수가 모두 양수여야 바로 짐으로 추가 (아니면 직접 입력으로 보완) */
    val hasAllDimensions: Boolean
        get() = listOf(widthCm, depthCm, heightCm).all { it != null && it > 0 }

    /** "120 × 60 × 73 cm", 모르는 치수는 "?" */
    val sizeLabel: String
        get() = listOf(widthCm, depthCm, heightCm).joinToString(" × ") { it?.let(::formatCm) ?: "?" } + " cm"

    /** 짐 목록용 정수 cm (반올림). 모르는 치수는 null */
    fun intCm(value: Double?): Int? = value?.takeIf { it > 0 }?.roundToInt()?.coerceIn(1, LoadCheckUiState.MAX_CM)

    companion object {
        /** 브랜드를 알 수 없을 때 (요청: IKEA로 표시) */
        const val DEFAULT_BRAND = "IKEA"

        fun from(response: ProductCandidateResponse) = ProductCandidateUi(
            candidateId = response.candidateId,
            brand = DEFAULT_BRAND,
            name = response.name,
            detail = listOfNotNull(response.variant, response.model).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
            transportState = TransportState.fromCode(response.transportState),
            widthCm = response.widthCm,
            depthCm = response.depthCm,
            heightCm = response.heightCm,
            sourceTitle = response.sources.orEmpty().firstOrNull()?.title,
        )

        /** 치수가 모두 있는 후보를 앞으로 (같으면 서버 순서) */
        fun sort(candidates: List<ProductCandidateUi>): List<ProductCandidateUi> =
            candidates.sortedByDescending { it.hasAllDimensions }

        private fun formatCm(value: Double): String =
            if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
    }
}

/** [label] 찾은 제품 규격 창, [shortLabel] 짐 카드 */
enum class TransportState(val code: String, val label: String, val shortLabel: String) {
    PACKAGED("PACKAGED", "포장된 상태", "포장 상태"),
    ASSEMBLED("ASSEMBLED", "조립된 상태", "조립 상태"),
    FOLDED("FOLDED", "접힌 상태", "접힌 상태"),
    ;

    companion object {
        fun fromCode(code: String?): TransportState = entries.firstOrNull { it.code == code } ?: ASSEMBLED
    }
}
