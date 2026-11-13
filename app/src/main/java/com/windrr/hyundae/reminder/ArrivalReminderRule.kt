package com.windrr.hyundae.reminder

/**
 * 도착·주차 리마인드를 띄울 시점을 판단한다. SDK와 분리된 순수 로직.
 *
 * 조건: 주행(P가 아닌 기어)을 한 번 이상 거친 뒤, 목적지 도착 이벤트와 P단이 모두 충족되면 1회만 true.
 * 도착 이벤트와 P단은 어느 쪽이 먼저 와도 된다. 도착 이벤트는 [arrivalValidMs] 동안만 유효.
 * 문 신호를 받을 수 있으면 도착·P단 이후 문(운전석·뒷좌석·트렁크)이 열리는 순간 알리고,
 * 문이 [doorWaitMs] 안에 열리지 않거나 문 신호를 받을 수 없으면 도착·P단 시점에 알린다.
 */
class ArrivalReminderRule(
    private val now: () -> Long = System::currentTimeMillis,
    private val arrivalValidMs: Long = DEFAULT_ARRIVAL_VALID_MS,
    val doorWaitMs: Long = DEFAULT_DOOR_WAIT_MS,
) {
    private var hasDriven = false
    private var isParked = false
    private var arrivedAt: Long? = null
    private var readyAt: Long? = null
    private var isDoorSignalAvailable = false
    private var fired = false

    val isWaitingForDoor: Boolean get() = !fired && readyAt != null

    fun setDoorSignalAvailable(available: Boolean) {
        isDoorSignalAvailable = available
    }

    /** @return 지금 리마인드를 띄워야 하면 true */
    fun onGearChanged(isPark: Boolean): Boolean {
        isParked = isPark
        if (!isPark) {
            hasDriven = true
            readyAt = null
        }
        return check()
    }

    /** @return 지금 리마인드를 띄워야 하면 true */
    fun onDestinationArrived(): Boolean {
        arrivedAt = now()
        return check()
    }

    fun onDoorOpened(): Boolean {
        if (fired || readyAt == null) return false
        return fire()
    }

    fun onDoorWaitElapsed(): Boolean {
        val waitingSince = readyAt ?: return false
        if (fired || now() - waitingSince < doorWaitMs) return false
        return fire()
    }

    private fun check(): Boolean {
        val arrivedRecently = arrivedAt?.let { now() - it <= arrivalValidMs } == true
        if (fired || readyAt != null || !hasDriven || !isParked || !arrivedRecently) return false
        if (!isDoorSignalAvailable) return fire()
        readyAt = now()
        return false
    }

    private fun fire(): Boolean {
        fired = true
        readyAt = null
        return true
    }

    companion object {
        const val DEFAULT_ARRIVAL_VALID_MS = 10 * 60 * 1000L
        const val DEFAULT_DOOR_WAIT_MS = 60 * 1000L
    }
}
