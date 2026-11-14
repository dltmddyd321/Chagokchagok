package com.windrr.hyundae.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 홈 상단 차량 상태 ("P · 주차 중", "트렁크 닫힘") */
data class VehicleStatus(
    /** null = 아직 모름 */
    val gear: VehicleGear? = null,
    /** null = 트렁크 신호 없음 */
    val trunkOpen: Boolean? = null,
    /** null = 운전석 문 신호 없음. 열리면 사람이 타는 중 → 최신 계획 동기화 계기 */
    val driverDoorOpen: Boolean? = null,
)

/** Pleos Vehicle SDK로 기어·트렁크(REAR)·운전석 문(ROW_1_LEFT) 상태를 구독 */
class VehicleStatusSource(context: Context) {

    private val _status = MutableStateFlow(VehicleStatus())
    val status: StateFlow<VehicleStatus> = _status.asStateFlow()

    private var vehicle: Vehicle? = null

    /** 구독 중인 문 영역 → 리스너 (해제용) */
    private val doorListeners = mutableMapOf<DoorArea, DoorPositionListener>()

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _status.update { it.copy(gear = gear) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "기어 구독 실패", e)
        }
    }

    init {
        runCatching {
            vehicle = Vehicle(context).apply {
                initialize()
                getGear().registerGearSelection(gearListener)
                watchDoors(this)
            }
        }.onFailure { Log.w(TAG, "Vehicle SDK 연결 실패", it) }
    }

    private fun watchDoors(vehicle: Vehicle) {
        val door = vehicle.getDoor()
        door.checkDoorPositionCapabilities(
            { _, bounds ->
                bounds.forEach { (area, areaBounds) ->
                    val update: ((Boolean) -> Unit)? = when {
                        DoorValue.REAR in area.values -> { open -> _status.update { it.copy(trunkOpen = open) } }
                        DoorValue.ROW_1_LEFT in area.values -> { open -> _status.update { it.copy(driverDoorOpen = open) } }
                        else -> null
                    }
                    update?.let {
                        val listener = doorListener(areaBounds, it)
                        door.registerDoorPosition(area, listener)
                        doorListeners[area] = listener
                    }
                }
            },
            { Log.w(TAG, "문 위치 지원 여부 확인 실패", it) },
        )
    }

    private fun doorListener(bounds: AreaBounds<Int>, onOpenChanged: (Boolean) -> Unit) = object : DoorPositionListener {
        override fun onPositionUpdated(position: Int?) {
            position ?: return
            onOpenChanged(position > bounds.minValue)
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "문 위치 구독 실패", e)
        }
    }

    fun release() {
        vehicle?.let { v ->
            runCatching { v.getGear().unregisterGearSelection(gearListener) }
            doorListeners.forEach { (area, listener) -> runCatching { v.getDoor().unregisterDoorPosition(area, listener) } }
            runCatching { v.release() }
        }
        doorListeners.clear()
        vehicle = null
    }

    private companion object {
        const val TAG = "VehicleStatusSource"
    }
}
