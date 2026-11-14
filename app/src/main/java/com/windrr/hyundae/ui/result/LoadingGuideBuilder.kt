package com.windrr.hyundae.ui.result

import com.windrr.hyundae.data.local.entity.SeatCode
import com.windrr.hyundae.data.remote.model.LoadingGuideResponse

/** 가이드 한 단계 (화면용). [number]는 1부터, 이미지 배지와 같은 번호 */
data class GuideStepUi(
    val number: Int,
    val title: String,
    val body: String?,
    /** 배지를 붙일 짐 (좌석 접기 단계면 null) */
    val placementId: String? = null,
    /** 배지를 붙일 접는 좌석 묶음 (짐 단계면 null) */
    val foldedGroup: FoldedSeatGroup? = null,
)

/**
 * 서버 적재 안내 → 화면 단계.
 * 서버 단계는 짐(placement)마다 하나라 "좌석 접기"가 따로 없다. 시안처럼 접기 단계를 앱이 앞에 붙이고, 이어서 짐 단계.
 * 서버가 단계를 비워 주면(FALLBACK·실패) 배치의 싣는 순서로 기본 문구를 만든다.
 */
internal object LoadingGuideBuilder {

    fun build(layout: ArrangementLayoutUi, guide: LoadingGuideResponse?): List<GuideStepUi> {
        // 시안: "2열 좌석을 모두 접으세요 / 운전석과 조수석은 그대로 둡니다."
        val folded = layout.foldedGroups.flatMap { it.seats }.toSet()
        val allSecondRow = folded.containsAll(listOf(SeatCode.ROW_2_LEFT, SeatCode.ROW_2_CENTER, SeatCode.ROW_2_RIGHT))
        val foldSteps = when {
            folded.isEmpty() -> emptyList()
            allSecondRow && SeatCode.ROW_1_RIGHT !in folded -> listOf(
                Step(title = "2열 좌석을 모두 접으세요", body = "운전석과 조수석은 그대로 둡니다.", foldedGroup = layout.foldedGroups.first()),
            )
            else -> layout.foldedGroups.map { group ->
                val name = seatGroupName(group.seats)
                Step(title = "${name}${Josa.을를(name)} 접으세요", body = "나머지 좌석은 그대로 둡니다.", foldedGroup = group)
            }
        }
        val itemsById = layout.items.associateBy { it.placementId }
        val serverSteps = guide?.steps.orEmpty()
            .filter { it.text.isNotBlank() && it.placementId in itemsById }
            .sortedBy { it.order }
            .map { step ->
                val (title, body) = splitText(step.text)
                Step(title = title, body = body, placementId = step.placementId)
            }
        // 서버 문구가 없으면 시안처럼: 짐 싣기 한 단계 + 고정하기
        val itemSteps = serverSteps.ifEmpty { fallbackSteps(layout) }
        return (foldSteps + itemSteps).mapIndexed { index, step ->
            GuideStepUi(index + 1, step.title, step.body, step.placementId, step.foldedGroup)
        }
    }

    /** 첫 줄 = 제목, 나머지 = 설명 */
    fun splitText(text: String): Pair<String, String?> {
        val lines = text.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        return lines.first() to lines.drop(1).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun fallbackSteps(layout: ArrangementLayoutUi): List<Step> {
        val names = layout.items.map { it.name }.distinct()
        val joined = Josa.joinNames(names)
        val countWord = when (layout.items.size) {
            1 -> "짐을"
            2 -> "두 짐을"
            3 -> "세 짐을"
            else -> "짐을 모두"
        }
        return listOf(
            Step(title = "$joined${Josa.을를(joined)} 실으세요", body = "화살표 방향으로 $countWord 넣으세요."),
            Step(title = "짐이 움직이지 않게 고정하세요", body = "짐이 좌석에 닿지 않는지 확인하세요."),
        )
    }

    private data class Step(
        val title: String,
        val body: String?,
        val placementId: String? = null,
        val foldedGroup: FoldedSeatGroup? = null,
    )
}
