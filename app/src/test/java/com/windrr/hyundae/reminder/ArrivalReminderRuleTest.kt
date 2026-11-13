package com.windrr.hyundae.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalReminderRuleTest {

    private var clock = 0L
    private val rule = ArrivalReminderRule(now = { clock }, arrivalValidMs = 1_000)

    @Test
    fun drive_arrive_thenPark_fires() {
        assertFalse(rule.onGearChanged(isPark = false))
        assertFalse(rule.onDestinationArrived())
        assertTrue(rule.onGearChanged(isPark = true))
    }

    @Test
    fun drive_park_thenArrive_fires() {
        rule.onGearChanged(isPark = false)
        assertFalse(rule.onGearChanged(isPark = true))
        assertTrue(rule.onDestinationArrived())
    }

    @Test
    fun parkWithoutDriving_doesNotFire() {
        // 앱을 켰을 때 이미 P단인 상태 (구독 직후 현재값 콜백)
        assertFalse(rule.onGearChanged(isPark = true))
        assertFalse(rule.onDestinationArrived())
    }

    @Test
    fun parkWithoutArrival_doesNotFire() {
        // 편의점 등 목적지가 아닌 곳에서 잠깐 정차
        rule.onGearChanged(isPark = false)
        assertFalse(rule.onGearChanged(isPark = true))
    }

    @Test
    fun staleArrival_doesNotFire() {
        rule.onGearChanged(isPark = false)
        rule.onDestinationArrived()
        clock = 2_000
        assertFalse(rule.onGearChanged(isPark = true))
    }

    @Test
    fun firesOnlyOnce() {
        rule.onGearChanged(isPark = false)
        rule.onDestinationArrived()
        assertTrue(rule.onGearChanged(isPark = true))
        rule.onGearChanged(isPark = false)
        assertFalse(rule.onGearChanged(isPark = true))
        assertFalse(rule.onDestinationArrived())
    }

    private val doorRule = ArrivalReminderRule(now = { clock }, arrivalValidMs = 1_000, doorWaitMs = 500).apply {
        setDoorSignalAvailable(true)
    }

    private fun driveArriveAndPark(target: ArrivalReminderRule): Boolean {
        target.onGearChanged(isPark = false)
        target.onDestinationArrived()
        return target.onGearChanged(isPark = true)
    }

    @Test
    fun withDoorSignal_arriveAndPark_waitsForDoor() {
        assertFalse(driveArriveAndPark(doorRule))
        assertTrue(doorRule.isWaitingForDoor)
    }

    @Test
    fun withDoorSignal_doorOpenedAfterArriveAndPark_fires() {
        driveArriveAndPark(doorRule)
        assertTrue(doorRule.onDoorOpened())
        assertFalse(doorRule.isWaitingForDoor)
    }

    @Test
    fun withDoorSignal_doorOpenedBeforeParking_doesNotFire() {
        doorRule.onGearChanged(isPark = false)
        doorRule.onDestinationArrived()
        assertFalse(doorRule.onDoorOpened())
    }

    @Test
    fun withDoorSignal_doorNeverOpens_firesAfterWait() {
        driveArriveAndPark(doorRule)
        clock = 400
        assertFalse(doorRule.onDoorWaitElapsed())
        clock = 500
        assertTrue(doorRule.onDoorWaitElapsed())
    }

    @Test
    fun withDoorSignal_leavingParkWhileWaiting_cancelsWait() {
        driveArriveAndPark(doorRule)
        doorRule.onGearChanged(isPark = false)
        assertFalse(doorRule.isWaitingForDoor)
        assertFalse(doorRule.onDoorOpened())
        clock = 500
        assertFalse(doorRule.onDoorWaitElapsed())
    }

    @Test
    fun withDoorSignal_firesOnlyOnce() {
        driveArriveAndPark(doorRule)
        assertTrue(doorRule.onDoorOpened())
        assertFalse(doorRule.onDoorOpened())
        clock = 500
        assertFalse(doorRule.onDoorWaitElapsed())
    }
}
