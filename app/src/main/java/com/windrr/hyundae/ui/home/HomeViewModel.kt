package com.windrr.hyundae.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.windrr.hyundae.data.local.AppDatabase
import com.windrr.hyundae.data.vehicle.VehicleStatusSource
import com.windrr.hyundae.data.weather.WeatherRepository
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
            db.planDao().observePlans().collect { plans -> _state.update { it.copy(planCount = plans.size) } }
        }
        viewModelScope.launch {
            db.vehicleDao().observeMain().collect { car -> _state.update { it.copy(vehicleDesc = car?.desc.orEmpty()) } }
        }
        viewModelScope.launch {
            vehicleStatus.status.collect { s -> _state.update { it.copy(gear = s.gear, trunkOpen = s.trunkOpen) } }
        }
        loadWeather()
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.RefreshWeather -> loadWeather()
        }
    }

    private fun loadWeather() {
        viewModelScope.launch {
            _state.update { it.copy(weatherLoading = true) }
            val weather = weatherRepository.current()
            _state.update { it.copy(weather = weather ?: it.weather, weatherLoading = false) }
        }
    }

    override fun onCleared() {
        vehicleStatus.release()
    }
}
