package com.windrr.hyundae.ui.home

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.windrr.hyundae.data.local.relation.PlanWithDetails
import com.windrr.hyundae.data.weather.WeatherInfo

data class HomeUiState(
    /** 고정 데모 사용자 */
    val userName: String = "민정",
    /** "5인승 SUV" (대표 차량 설명) */
    val vehicleDesc: String = "",
    val planCount: Int = 0,
    /** 가장 최근 계획 (서버 동기화된 최신 1개). 없으면 null */
    val latestPlan: LatestPlanUi? = null,
    val weather: WeatherInfo? = null,
    val weatherLoading: Boolean = true,
    val gear: VehicleGear? = null,
    val trunkOpen: Boolean? = null,
) {
    /** "P · 주차 중" 등. 기어를 모르면 null */
    val gearLabel: String?
        get() = when (gear) {
            VehicleGear.GEAR_PARK -> "P · 주차 중"
            VehicleGear.GEAR_REVERSE -> "R · 후진 중"
            VehicleGear.GEAR_NEUTRAL -> "N · 중립"
            VehicleGear.GEAR_DRIVE -> "D · 주행 중"
            null -> null
            else -> null
        }

    val trunkLabel: String?
        get() = trunkOpen?.let { if (it) "트렁크 열림" else "트렁크 닫힘" }
}

/** 홈 왼쪽 큰 카드: 최신 계획 */
data class LatestPlanUi(
    val planId: String,
    /** "여행" */
    val categoryLabel: String,
    val name: String,
    /** 운전자 포함 */
    val passengerCount: Int,
    /** "유모차 1개 · 캐리어 1개 외 3개" */
    val itemsLine: String,
) {
    companion object {
        /** 이름까지 보여줄 짐 종류 수 (나머지는 "외 N개") */
        private const val SHOWN_ITEM_KINDS = 2

        fun from(details: PlanWithDetails): LatestPlanUi {
            val items = details.items
            val shown = items.take(SHOWN_ITEM_KINDS).joinToString(" · ") { "${it.name} ${it.quantity}개" }
            val rest = items.size - SHOWN_ITEM_KINDS
            return LatestPlanUi(
                planId = details.plan.planId,
                categoryLabel = details.plan.category.label,
                name = details.plan.name.ifBlank { "이름 없는 계획" },
                passengerCount = details.plan.passengerCount,
                itemsLine = when {
                    items.isEmpty() -> "등록된 짐 없음"
                    rest > 0 -> "$shown 외 ${rest}개"
                    else -> shown
                },
            )
        }
    }
}

sealed interface HomeIntent {
    data object RefreshWeather : HomeIntent
}
