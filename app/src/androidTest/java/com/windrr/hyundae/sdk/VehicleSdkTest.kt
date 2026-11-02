package com.windrr.hyundae.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 필요 퍼미션: CAR_INFO(VIN), CAR_DRIVE(기어·주행거리) */
@RunWith(AndroidJUnit4::class)
class VehicleSdkTest {

    private lateinit var vehicle: Vehicle

    @Before
    fun setUp() = onMain {
        vehicle = Vehicle(targetContext).apply { initialize() }
    }

    @After
    fun tearDown() = onMain { vehicle.release() }

    @Test
    fun getVin() {
        awaitWithRetry<String?>("Vehicle.CarInfo.getVINInfo") { ok, err ->
            vehicle.getCarInfo().getVINInfo(ok) { err(it.toString()) }
        }
    }

    @Test
    fun getCurrentGear() {
        awaitWithRetry<VehicleGear?>("Vehicle.Gear.getCurrentGearSelectionState") { ok, err ->
            vehicle.getGear().getCurrentGearSelectionState(ok) { err(it.toString()) }
        }
    }

    @Test
    fun getDistanceDriven() {
        awaitWithRetry<Float?>("Vehicle.Odometer.getCurrentDistanceDriven") { ok, err ->
            vehicle.getOdometer().getCurrentDistanceDriven(ok) { err(it.toString()) }
        }
    }
}
