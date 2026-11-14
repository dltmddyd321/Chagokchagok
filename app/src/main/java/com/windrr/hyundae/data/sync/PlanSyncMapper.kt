package com.windrr.hyundae.data.sync

import com.windrr.hyundae.data.local.entity.PlanCarSeatEntity
import com.windrr.hyundae.data.local.entity.PlanCategory
import com.windrr.hyundae.data.local.entity.PlanEntity
import com.windrr.hyundae.data.local.entity.PlanItemEntity
import com.windrr.hyundae.data.local.entity.SeatCode
import com.windrr.hyundae.data.local.entity.VehicleEntity
import com.windrr.hyundae.data.local.relation.PlanWithDetails
import com.windrr.hyundae.data.remote.model.PlanResponse
import com.windrr.hyundae.data.remote.model.PlanSummaryResponse
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/** 서버 계획 → Room 엔티티 변환 (SDK·DB와 분리된 순수 로직) */
internal object PlanSyncMapper {

    data class LocalPlan(
        val plan: PlanEntity,
        val items: List<PlanItemEntity>,
        val carSeats: List<PlanCarSeatEntity>,
    )

    /** 상세를 기본으로, 상세에 없는 값(차량 이름 등)은 목록 요약에서 채운다 */
    fun toLocal(summary: PlanSummaryResponse, detail: PlanResponse): LocalPlan {
        val planId = detail.planId
        val plan = PlanEntity(
            planId = planId,
            vehicleId = detail.vehicleId ?: summary.vehicleId ?: UNKNOWN_VEHICLE_ID,
            passengerCount = detail.passengerCount ?: summary.passengerCount ?: 1,
            memo = detail.memo ?: summary.memo,
            name = (detail.name ?: summary.name).orEmpty(),
            category = PlanCategory.fromCode(detail.category ?: summary.category),
            isFavorite = detail.isFavorite ?: summary.isFavorite ?: false,
            createdAt = parseEpochMs(detail.createdAt ?: summary.createdAt),
        )
        val items = detail.items.orEmpty().map { item ->
            PlanItemEntity(
                itemId = item.itemId,
                planId = planId,
                name = item.name?.takeIf { it.isNotBlank() } ?: "짐",
                widthCm = item.widthCm.toCm(),
                depthCm = item.depthCm.toCm(),
                heightCm = item.heightCm.toCm(),
                quantity = item.quantity ?: 1,
            )
        }
        val carSeats = detail.carSeats.orEmpty()
            .mapNotNull { code -> SeatCode.entries.firstOrNull { it.name == code } }
            .distinct()
            .map { PlanCarSeatEntity(planId = planId, seatCode = it) }
        return LocalPlan(plan, items, carSeats)
    }

    /** plan.vehicle_id 외래키를 맞추기 위한 차량 행. 기기에 없을 때만 넣는다 */
    fun vehicleFor(summary: PlanSummaryResponse, plan: PlanEntity): VehicleEntity =
        VehicleEntity(
            vehicleId = plan.vehicleId,
            isMain = false,
            name = summary.vehicleName?.takeIf { it.isNotBlank() } ?: "내 차",
            desc = "",
        )

    /** 기기에 같은 내용이 이미 있으면 다시 쓰지 않는다 (다시 쓰면 기기의 계산 결과가 지워짐) */
    fun isSame(existing: PlanWithDetails?, incoming: LocalPlan): Boolean =
        existing != null &&
            existing.plan == incoming.plan &&
            existing.items.sortedBy { it.itemId } == incoming.items.sortedBy { it.itemId } &&
            existing.carSeats.map { it.seatCode }.toSet() == incoming.carSeats.map { it.seatCode }.toSet()

    private fun Double?.toCm(): Int = this?.roundToInt() ?: 0

    private fun parseEpochMs(isoTime: String?): Long =
        isoTime?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            ?: System.currentTimeMillis()

    const val UNKNOWN_VEHICLE_ID = "unknown"
}
