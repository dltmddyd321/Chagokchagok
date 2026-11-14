package com.windrr.hyundae.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.windrr.hyundae.data.local.AppDatabase
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.windrr.hyundae.data.sync.PlanAutoSync
import com.windrr.hyundae.data.vehicle.VehicleStatus
import com.windrr.hyundae.data.vehicle.VehicleStatusSource
import com.windrr.hyundae.data.weather.WeatherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val weatherRepository = WeatherRepository(application)
    private val vehicleStatus = VehicleStatusSource(application)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            db.planDao().observePlans().collect { plans ->
                // 최근 등록 순이라 첫 번째가 최신 계획
                _state.update { it.copy(planCount = plans.size, latestPlan = plans.firstOrNull()?.let(LatestPlanUi::from)) }
            }
        }
        viewModelScope.launch {
            db.vehicleDao().observeMain().collect { car -> _state.update { it.copy(vehicleDesc = car?.desc.orEmpty()) } }
        }
        viewModelScope.launch {
            var previous: VehicleStatus? = null
            vehicleStatus.status.collect { s ->
                _state.update { it.copy(gear = s.gear, trunkOpen = s.trunkOpen) }
                syncOnVehicleSignal(previous, s)
                previous = s
            }
        }
        // 날씨: 처음 한 번 + 1시간마다 자동 갱신 (주행 중 지역·날씨가 바뀌므로)
        viewModelScope.launch {
            while (true) {
                loadWeatherNow()
                delay(WEATHER_REFRESH_MS)
            }
        }
    }

    /** 운전석 문이 열리거나(타는 중) 기어가 P에서 빠지면(출발 직전) 최신 계획을 다시 받는다 */
    private fun syncOnVehicleSignal(previous: VehicleStatus?, current: VehicleStatus) {
        val doorOpened = previous?.driverDoorOpen == false && current.driverDoorOpen == true
        val leftPark = previous?.gear == VehicleGear.GEAR_PARK && current.gear != null && current.gear != VehicleGear.GEAR_PARK
        val reason = when {
            doorOpened -> "운전석 문 열림"
            leftPark -> "P단 → 주행"
            else -> return
        }
        viewModelScope.launch { PlanAutoSync.sync(getApplication(), reason) }
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.RefreshWeather -> loadWeather()
        }
    }

    private fun loadWeather() {
        viewModelScope.launch { loadWeatherNow() }
    }

    private suspend fun loadWeatherNow() {
        _state.update { it.copy(weatherLoading = true) }
        val weather = weatherRepository.current()
        // 실패하면 이전 날씨를 그대로 둔다
        _state.update { it.copy(weather = weather ?: it.weather, weatherLoading = false) }
    }

    private companion object {
        const val WEATHER_REFRESH_MS = 60 * 60 * 1000L
    }

    override fun onCleared() {
        vehicleStatus.release()
    }
}
