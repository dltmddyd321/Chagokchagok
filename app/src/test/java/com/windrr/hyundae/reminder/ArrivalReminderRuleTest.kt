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
}
