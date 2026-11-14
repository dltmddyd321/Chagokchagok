package com.windrr.hyundae.data.sync

import com.windrr.hyundae.data.local.entity.PlanCategory
import com.windrr.hyundae.data.local.entity.SeatCode
import com.windrr.hyundae.data.local.relation.PlanWithDetails
import com.windrr.hyundae.data.remote.model.PlanItemResponse
import com.windrr.hyundae.data.remote.model.PlanResponse
import com.windrr.hyundae.data.remote.model.PlanSummaryResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanSyncMapperTest {

    private val summary = PlanSummaryResponse(
        planId = "p1", name = "목록 이름", category = "LEISURE", memo = null, isFavorite = true,
        vehicleId = "avante_cn8", vehicleName = "더 뉴 아반떼", passengerCount = 3,
        itemTypeCount = 1, itemTotalQuantity = 2, createdAt = "2026-11-14T09:00:00+09:00",
        revision = 1, favoritedAt = null, itemSummaries = null,
    )

    private val detail = PlanResponse(
        planId = "p1", name = "주말 캠핑", category = null, memo = "텐트 먼저", isFavorite = null,
        vehicleId = "avante_cn8", passengerCount = 3, carSeats = listOf("ROW_2_LEFT", "BAD"), retainedSeats = null,
        items = listOf(PlanItemResponse("i1", "캐리어", 40.4, 25.6, 60.0, 2)),
        createdAt = "2026-11-14T09:00:00+09:00", latestCalculationId = null,
    )

    @Test
    fun toLocal_usesDetailFirst_thenSummary() {
        val local = PlanSyncMapper.toLocal(summary, detail)
        assertEquals("주말 캠핑", local.plan.name)
        assertEquals(PlanCategory.LEISURE, local.plan.category) // 상세에 없으면 목록 값
        assertTrue(local.plan.isFavorite)
        assertEquals("텐트 먼저", local.plan.memo)
        assertEquals(1_794_614_400_000L, local.plan.createdAt)
    }

    @Test
    fun toLocal_roundsDimensions_andDropsUnknownSeats() {
        val local = PlanSyncMapper.toLocal(summary, detail)
        val item = local.items.single()
        assertEquals(listOf(40, 26, 60, 2), listOf(item.widthCm, item.depthCm, item.heightCm, item.quantity))
        assertEquals(listOf(SeatCode.ROW_2_LEFT), local.carSeats.map { it.seatCode })
    }

    @Test
    fun isSame_detectsUnchangedPlan() {
        val local = PlanSyncMapper.toLocal(summary, detail)
        val existing = PlanWithDetails(local.plan, vehicle = null, items = local.items, carSeats = local.carSeats)
        assertTrue(PlanSyncMapper.isSame(existing, local))
        assertFalse(PlanSyncMapper.isSame(existing.copy(items = emptyList()), local))
        assertFalse(PlanSyncMapper.isSame(null, local))
    }
}
