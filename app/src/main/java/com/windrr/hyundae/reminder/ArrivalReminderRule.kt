package com.windrr.hyundae.reminder

/**
 * 도착·주차 리마인드를 띄울 시점을 판단한다. SDK와 분리된 순수 로직.
 *
 * 조건: 주행(P가 아닌 기어)을 한 번 이상 거친 뒤, 목적지 도착 이벤트와 P단이 모두 충족되면 1회만 true.
 * 도착 이벤트와 P단은 어느 쪽이 먼저 와도 된다. 도착 이벤트는 [arrivalValidMs] 동안만 유효.
 */
class ArrivalReminderRule(
    private val now: () -> Long = System::currentTimeMillis,
    private val arrivalValidMs: Long = DEFAULT_ARRIVAL_VALID_MS,
) {
    private var hasDriven = false
    private var isParked = false
    private var arrivedAt: Long? = null
    private var fired = false

    /** @return 지금 리마인드를 띄워야 하면 true */
    fun onGearChanged(isPark: Boolean): Boolean {
        isParked = isPark
        if (!isPark) hasDriven = true
        return check()
    }

    /** @return 지금 리마인드를 띄워야 하면 true */
    fun onDestinationArrived(): Boolean {
        arrivedAt = now()
        return check()
    }

    private fun check(): Boolean {
        val arrivedRecently = arrivedAt?.let { now() - it <= arrivalValidMs } == true
        if (fired || !hasDriven || !isParked || !arrivedRecently) return false
        fired = true
        return true
    }

    companion object {
        const val DEFAULT_ARRIVAL_VALID_MS = 10 * 60 * 1000L
    }
}
