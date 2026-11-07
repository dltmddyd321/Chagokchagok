package com.windrr.hyundae.sdk

import ai.pleos.playground.vehicle.Vehicle
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "내 차에 실릴까?" 기획용 탐색 테스트 — 적재 판단에 쓸 차량 값이 실제로 읽히는지 logcat(PleosSdkTest)으로 확인한다.
 * 실패 조건 없이 값만 기록한다.
 */
@RunWith(AndroidJUnit4::class)
class CargoSignalsProbeTest {

    private lateinit var vehicle: Vehicle

    @Before
    fun setUp() = onMain {
        vehicle = Vehicle(targetContext).apply { initialize() }
    }

    @After
    fun tearDown() = onMain { vehicle.release() }

    @Test
    fun probeSeats() {
        val seat = vehicle.getSeat()
        runCatching {
            val areas = awaitWithRetry<List<*>>("Seat.Occupancy.areas") { ok, err -> seat.checkSeatOccupancyCapabilities({ _, a -> ok(a) }) { err(it.toString()) } }
            areas.filterIsInstance<ai.pleos.playground.vehicle.api.model.area.SeatArea>().forEach { area ->
                runCatching { awaitCallback<Boolean?>("Seat.Occupancy[${area.values.joinToString()}]") { ok, err -> seat.getCurrentSeatOccupancy(area, ok) { err(it.toString()) } } }
            }
        }
        runCatching {
            val bounds = awaitWithRetry<Map<ai.pleos.playground.vehicle.api.model.area.SeatArea, *>>("Seat.BackrestAngle.bounds") { ok, err ->
                seat.checkSeatBackrestAnglePositionCapabilities({ _, b -> ok(b) }) { err(it.toString()) }
            }
            bounds.forEach { (area, b) ->
                runCatching { awaitCallback<Int?>("Seat.BackrestAngle[${area.values.joinToString()}] bounds=$b") { ok, err -> seat.getCurrentSeatBackrestAnglePosition(area, ok) { err(it.toString()) } } }
            }
        }
        runCatching {
            val bounds = awaitWithRetry<Map<ai.pleos.playground.vehicle.api.model.area.SeatArea, *>>("Seat.ForeAft.bounds") { ok, err ->
                seat.checkSeatForeAftPositionCapabilities({ _, b -> ok(b) }) { err(it.toString()) }
            }
            bounds.forEach { (area, b) ->
                runCatching { awaitCallback<Int?>("Seat.ForeAft[${area.values.joinToString()}] bounds=$b") { ok, err -> seat.getCurrentSeatForeAftPosition(area, ok) { err(it.toString()) } } }
            }
        }
    }

    @Test
    fun probeDoors() {
        val door = vehicle.getDoor()
        runCatching {
            val bounds = awaitWithRetry<Map<ai.pleos.playground.vehicle.api.model.area.DoorArea, *>>("Door.Position.bounds") { ok, err ->
                door.checkDoorPositionCapabilities({ _, b -> ok(b) }) { err(it.toString()) }
            }
            bounds.forEach { (area, b) ->
                runCatching { awaitCallback<Int?>("Door.Position[${area.values.joinToString()}] bounds=$b") { ok, err -> door.getCurrentDoorPosition(area, ok) { err(it.toString()) } } }
            }
        }
    }

    /** Pleos CarInfo는 VIN·CRN만 제공 → 차종 판별 대안으로 AAOS 표준 INFO_* 속성 확인 */
    @Test
    fun probeCarInfo() {
        runCatching { awaitWithRetry<String?>("CarInfo.VIN(Pleos)") { ok, err -> vehicle.getCarInfo().getVINInfo(ok) { err(it.toString()) } } }
        val car = Car.createCar(targetContext)
        try {
            val cpm = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            mapOf(
                "INFO_MAKE" to VehiclePropertyIds.INFO_MAKE,
                "INFO_MODEL" to VehiclePropertyIds.INFO_MODEL,
                "INFO_MODEL_YEAR" to VehiclePropertyIds.INFO_MODEL_YEAR,
                "INFO_VIN" to VehiclePropertyIds.INFO_VIN,
                "INFO_EXTERIOR_DIMENSIONS" to VehiclePropertyIds.INFO_EXTERIOR_DIMENSIONS,
                "INFO_FUEL_TYPE" to VehiclePropertyIds.INFO_FUEL_TYPE,
            ).forEach { (name, id) ->
                val v = runCatching { cpm.getProperty<Any>(id, 0)?.value }
                val shown = v.map { if (it is IntArray) it.joinToString() else if (it is Array<*>) it.joinToString() else it.toString() }
                Log.i(TAG, "[AAOS.$name] ${shown.getOrElse { "ERROR ${it.javaClass.simpleName}: ${it.message}" }}")
            }
        } finally {
            car.disconnect()
        }
    }
}
