package com.windrr.hyundae.ui.result

import com.windrr.hyundae.data.local.entity.SeatCode
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadGuideTextTest {

    @Test
    fun josa_followsLastSound() {
        assertEquals("책상과 물건 S", Josa.joinNames(listOf("책상", "물건 S")))
        assertEquals("를", Josa.을를("책상과 물건 S"))
        assertEquals("캐리어와 유모차", Josa.joinNames(listOf("캐리어", "유모차")))
        assertEquals("을", Josa.을를("캠핑박스 L"))
        assertEquals("캐리어 외 2개", Josa.joinNames(listOf("캐리어", "유모차", "책상")))
    }

    @Test
    fun fallbackSteps_matchDesign() {
        val layout = ArrangementLayoutUi(
            arrangementId = "a",
            seats = emptyList(),
            items = listOf(
                ItemBox("p1", "책상", 0, PxRect(0f, 0f, 1f, 1f), "TRUNK"),
                ItemBox("p2", "물건 S", 1, PxRect(0f, 0f, 1f, 1f), "TRUNK"),
            ),
            foldedGroups = listOf(
                FoldedSeatGroup(listOf(SeatCode.ROW_2_LEFT, SeatCode.ROW_2_CENTER)),
                FoldedSeatGroup(listOf(SeatCode.ROW_2_RIGHT)),
            ),
        )
        val steps = LoadingGuideBuilder.build(layout, guide = null)
        assertEquals(
            listOf("2열 좌석을 모두 접으세요", "책상과 물건 S를 실으세요", "짐이 움직이지 않게 고정하세요"),
            steps.map { it.title },
        )
        assertEquals("화살표 방향으로 두 짐을 넣으세요.", steps[1].body)
        assertEquals(listOf(1, 2, 3), steps.map { it.number })
    }
}
