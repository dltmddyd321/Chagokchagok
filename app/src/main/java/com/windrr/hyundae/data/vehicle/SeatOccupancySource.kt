package com.windrr.hyundae.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 좌석 착석 센서 (Vehicle SDK Seat.registerSeatOccupancy, 권한 CAR_SEATS).
 * 운전석을 뺀 좌석 중 앉은 좌석 수 = 동승자 수 기본값. 어디 앉았는지는 쓰지 않는다 (서버가 자리를 정함).
 * 센서가 있는 좌석만 센다. 카시트·빈 카시트는 구분하지 못해서 사용자가 −/+로 고친다.
 */
class SeatOccupancySource(context: Context) {

    /** 센서로 잡은 동승자 수. null = 센서 없음·아직 모름 */
    private val _companions = MutableStateFlow<Int?>(null)
    val companions: StateFlow<Int?> = _companions.asStateFlow()

    private var vehicle: Vehicle? = null
    private val listeners = mutableMapOf<SeatArea, SeatOccupancyListener>()
    private val occupied = mutableMapOf<SeatArea, Boolean>()

    init {
        runCatching {
            vehicle = Vehicle(context).apply {
                initialize()
                watchSeats(this)
            }
        }.onFailure { Log.w(TAG, "Vehicle SDK 연결 실패", it) }
    }

    private fun watchSeats(vehicle: Vehicle) {
        val seat = vehicle.getSeat()
        seat.checkSeatOccupancyCapabilities(
            { _, areas ->
                // 운전석은 늘 사람이 있으니 동승자 계산에서 뺀다
                areas.filterNot { SeatValue.ROW_1_LEFT in it.values }.forEach { area ->
                    val listener = object : SeatOccupancyListener {
                        override fun onSeatOccupancyUpdated(occupancy: Boolean?) {
                            synchronized(occupied) {
                                if (occupancy == null) occupied.remove(area) else occupied[area] = occupancy
                                _companions.value = if (occupied.isEmpty()) null else occupied.values.count { it }
                            }
                        }

                        override fun onFailed(e: Exception) {
                            Log.w(TAG, "착석 구독 실패", e)
                        }
                    }
                    seat.registerSeatOccupancy(area, listener)
                    listeners[area] = listener
                }
            },
            { Log.w(TAG, "착석 센서 지원 여부 확인 실패", it) },
        )
    }

    fun release() {
        vehicle?.let { v ->
            listeners.forEach { (area, listener) -> runCatching { v.getSeat().unregisterSeatOccupancy(area, listener) } }
            runCatching { v.release() }
        }
        listeners.clear()
        vehicle = null
    }

    private companion object {
        const val TAG = "SeatOccupancy"
    }
}
