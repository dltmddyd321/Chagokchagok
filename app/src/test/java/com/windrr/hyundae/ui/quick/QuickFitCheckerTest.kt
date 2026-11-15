package com.windrr.hyundae.ui.quick

import com.windrr.hyundae.data.local.DefaultCargoSpaces
import org.junit.Assert.assertEquals
import org.junit.Test

/** 투싼 하이브리드 실측: 트렁크 123×100×80(입구 116×80), 2열 접음 123×160×70 */
class QuickFitCheckerTest {

    private val spaces = DefaultCargoSpaces.ALL

    @Test
    fun suitcase_fitsTrunk() {
        assertEquals(QuickFit.FITS, QuickFitChecker.check(40.0, 25.0, 60.0, spaces))
    }

    @Test
    fun assembledDesk_needsFolding() {
        // 락캅텐/아딜스 책상 120×60×73: 앞뒤 100 트렁크엔 120이 안 눕고, 입구 폭 116에 세로로도 안 들어감 → 2열 접으면 들어감
        assertEquals(QuickFit.FITS_WHEN_FOLDED, QuickFitChecker.check(120.0, 60.0, 73.0, spaces))
    }

    @Test
    fun tooBig_notFit() {
        assertEquals(QuickFit.NOT_FIT, QuickFitChecker.check(200.0, 90.0, 90.0, spaces))
    }
}
