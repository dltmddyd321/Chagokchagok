package com.windrr.hyundae.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.windrr.hyundae.data.local.AppDatabase
import com.windrr.hyundae.data.sync.PlanSyncRepository
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
    private val planSync = PlanSyncRepository(application)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            db.planDao().observePlans().collect { plans -> _state.update { it.copy(planCount = plans.size) } }
        }
        viewModelScope.launch {
            db.vehicleDao().observeMain().collect { car -> _state.update { it.copy(vehicleDesc = car?.desc.orEmpty()) } }
        }
        viewModelScope.launch {
            vehicleStatus.status.collect { s -> _state.update { it.copy(gear = s.gear, trunkOpen = s.trunkOpen) } }
        }
        // 서버 계획·짐 목록을 기기 DB에 미리 받아 둔다 (주행 중 통신이 끊겨도 도착 알림·적재 확인 가능)
        viewModelScope.launch { planSync.syncRecentPlans() }
        // 날씨: 처음 한 번 + 1시간마다 자동 갱신 (주행 중 지역·날씨가 바뀌므로)
        viewModelScope.launch {
            while (true) {
                loadWeatherNow()
                delay(WEATHER_REFRESH_MS)
            }
        }
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
