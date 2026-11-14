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
)

/** Pleos Vehicle SDK로 기어·트렁크(REAR 도어) 상태를 구독 */
class VehicleStatusSource(context: Context) {

    private val _status = MutableStateFlow(VehicleStatus())
    val status: StateFlow<VehicleStatus> = _status.asStateFlow()

    private var vehicle: Vehicle? = null
    private var trunkArea: DoorArea? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _status.update { it.copy(gear = gear) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "기어 구독 실패", e)
        }
    }

    private var trunkListener: DoorPositionListener? = null

    init {
        runCatching {
            vehicle = Vehicle(context).apply {
                initialize()
                getGear().registerGearSelection(gearListener)
                watchTrunk(this)
            }
        }.onFailure { Log.w(TAG, "Vehicle SDK 연결 실패", it) }
    }

    private fun watchTrunk(vehicle: Vehicle) {
        val door = vehicle.getDoor()
        door.checkDoorPositionCapabilities(
            { _, bounds ->
                val entry = bounds.entries.firstOrNull { (area, _) -> DoorValue.REAR in area.values } ?: return@checkDoorPositionCapabilities
                val listener = trunkListener(entry.value)
                door.registerDoorPosition(entry.key, listener)
                trunkArea = entry.key
                trunkListener = listener
            },
            { Log.w(TAG, "트렁크 지원 여부 확인 실패", it) },
        )
    }

    private fun trunkListener(bounds: AreaBounds<Int>) = object : DoorPositionListener {
        override fun onPositionUpdated(position: Int?) {
            position ?: return
            _status.update { it.copy(trunkOpen = position > bounds.minValue) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "트렁크 구독 실패", e)
        }
    }

    fun release() {
        vehicle?.let { v ->
            runCatching { v.getGear().unregisterGearSelection(gearListener) }
            val area = trunkArea
            val listener = trunkListener
            if (area != null && listener != null) runCatching { v.getDoor().unregisterDoorPosition(area, listener) }
            runCatching { v.release() }
        }
        vehicle = null
    }

    private companion object {
        const val TAG = "VehicleStatusSource"
    }
}
