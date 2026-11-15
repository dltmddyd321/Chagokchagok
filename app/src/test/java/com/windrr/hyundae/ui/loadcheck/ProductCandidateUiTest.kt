package com.windrr.hyundae.ui.loadcheck

import com.windrr.hyundae.data.remote.model.ProductCandidateResponse
import com.windrr.hyundae.data.remote.model.ProductSourceResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCandidateUiTest {

    private fun response(w: Double?, d: Double?, h: Double?, state: String = "ASSEMBLED") = ProductCandidateResponse(
        candidateId = "c-$state",
        name = "락캅텐 / 아딜스 책상",
        model = "895.840.27",
        variant = "그레이/목재효과 블랙",
        transportState = state,
        widthCm = w,
        depthCm = d,
        heightCm = h,
        sources = listOf(ProductSourceResponse("https://example.com", "제품 페이지")),
        requiresConfirmation = true,
    )

    @Test
    fun mapsAllFields_withDefaultBrand() {
        val ui = ProductCandidateUi.from(response(120.0, 60.0, 73.0))
        assertEquals("IKEA", ui.brand)
        assertEquals("그레이/목재효과 블랙 · 895.840.27", ui.detail)
        assertEquals("120 × 60 × 73 cm", ui.sizeLabel)
        assertEquals(TransportState.ASSEMBLED, ui.transportState)
        assertEquals("제품 페이지", ui.sourceTitle)
        assertTrue(ui.hasAllDimensions)
    }

    @Test
    fun unknownDimension_isQuestionMark_andNotReady() {
        val ui = ProductCandidateUi.from(response(121.5, null, 8.0, "PACKAGED"))
        assertEquals("121.5 × ? × 8 cm", ui.sizeLabel)
        assertFalse(ui.hasAllDimensions)
        assertEquals(122, ui.intCm(ui.widthCm))
        assertNull(ui.intCm(ui.depthCm))
    }

    @Test
    fun sort_putsCompleteCandidatesFirst() {
        val partial = ProductCandidateUi.from(response(121.0, null, 8.0, "PACKAGED"))
        val full = ProductCandidateUi.from(response(120.0, 60.0, 73.0))
        assertEquals(listOf(full, partial), ProductCandidateUi.sort(listOf(partial, full)))
    }

    @Test
    fun shortName_dropsUppercaseEnglish_whenKoreanPresent() {
        org.junit.Assert.assertEquals("락캅텐 / 아딜스 책상", ProductCandidateUi.shortName("LAGKAPTEN 락캅텐 / ADILS 아딜스 책상"))
        org.junit.Assert.assertEquals("POÄNG 포엥 암체어".let { "포엥 암체어" }, ProductCandidateUi.shortName("POÄNG 포엥 암체어"))
        org.junit.Assert.assertEquals("Desk Lamp", ProductCandidateUi.shortName("Desk Lamp"))
    }
}
