package com.windrr.hyundae.ui.home

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.windrr.hyundae.data.weather.WeatherInfo

data class HomeUiState(
    /** 고정 데모 사용자 */
    val userName: String = "민정",
    /** "5인승 SUV" (대표 차량 설명) */
    val vehicleDesc: String = "",
    val planCount: Int = 0,
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

sealed interface HomeIntent {
    data object RefreshWeather : HomeIntent
}
