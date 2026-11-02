package com.windrr.hyundae.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Vehicle Control(Set API) 검증: 지원 범위 확인 → 현재값 조회 → 다른 값으로 변경 → 재조회로 반영 확인 → 원복.
 * 필요 퍼미션: CAR_DOORS·CONTROL_CAR_DOORS, CAR_WINDOWS·CONTROL_CAR_WINDOWS, CAR_CLIMATE·CONTROL_CAR_CLIMATE
 *
 * 2026-11-02 에뮬레이터(Pleos_Connect_v2.1.0) 결과:
 * - Door/Window: 퍼미션 검증·VHAL set 성공 응답까지 오지만 재조회 값이 바뀌지 않음(에뮬레이터가 제어를 반영하지 않음)
 * - HVAC FanSpeed: PropertyAsyncError code 2(NOT_AVAILABLE)
 * 콘솔상 Vehicle Control은 Coming Soon. 전체 스위트 결과를 흐리지 않도록 기본 Ignore — 확인할 때만 해제
 */
@Ignore("에뮬레이터에서 제어 값이 반영되지 않음 — 수동 확인용")
@RunWith(AndroidJUnit4::class)
class VehicleControlSdkTest {

    private lateinit var vehicle: Vehicle

    @Before
    fun setUp() = onMain {
        vehicle = Vehicle(targetContext).apply { initialize() }
    }

    @After
    fun tearDown() = onMain { vehicle.release() }

    @Test
    fun setDoorPosition() {
        val door = vehicle.getDoor()
        verifySetAndReadBack<DoorArea>(
            name = "Door.Position",
            checkCapabilities = { ok, err -> door.checkDoorPositionCapabilities({ config, areas -> ok(config to areas) }) { err(it.toString()) } },
            get = { area, ok, err -> door.getCurrentDoorPosition(area, ok) { err(it.toString()) } },
            set = { area, value, ok, err -> door.setDoorPosition(area, value, { _, v -> ok(v) }) { err(it.toString()) } },
        )
    }

    @Test
    fun setWindowPosition() {
        val window = vehicle.getWindow()
        verifySetAndReadBack<WindowArea>(
            name = "Window.Position",
            checkCapabilities = { ok, err -> window.checkWindowPositionCapabilities({ config, areas -> ok(config to areas) }) { err(it.toString()) } },
            get = { area, ok, err -> window.getCurrentWindowPosition(area, ok) { err(it.toString()) } },
            set = { area, value, ok, err -> window.setWindowPosition(area, value, { _, v -> ok(v) }) { err(it.toString()) } },
        )
    }

    @Test
    fun setHvacFanSpeed() {
        val hvac = vehicle.getHVAC()
        verifySetAndReadBack<SeatArea>(
            name = "HVAC.FanSpeed",
            checkCapabilities = { ok, err -> hvac.checkHvacFanSpeedCapabilities({ config, areas -> ok(config to areas) }) { err(it.toString()) } },
            get = { area, ok, err -> hvac.getHvacFanSpeed(area, ok) { err(it.toString()) } },
            set = { area, value, ok, err -> hvac.setHvacFanSpeed(area, value, { _, v -> ok(v) }) { err(it.toString()) } },
        )
    }

    private fun <A> verifySetAndReadBack(
        name: String,
        checkCapabilities: (ok: (Pair<CapabilityConfig, Map<A, AreaBounds<Int>>>) -> Unit, err: (String) -> Unit) -> Unit,
        get: (area: A, ok: (Int?) -> Unit, err: (String) -> Unit) -> Unit,
        set: (area: A, value: Int, ok: (Int?) -> Unit, err: (String) -> Unit) -> Unit,
    ) {
        val (config, areas) = awaitWithRetry("$name.checkCapabilities", block = checkCapabilities)
        Log.i(TAG, "[$name] accessType=${config.accessType}, areas=${areas.size}")
        assertTrue("[$name] 지원 영역 없음", areas.isNotEmpty())

        val (area, bounds) = areas.entries.first()
        val original = awaitCallback<Int?>("$name.get(before)") { ok, err -> get(area, ok, err) } ?: bounds.minValue
        val target = if (original == bounds.maxValue) bounds.minValue else bounds.maxValue

        awaitCallback<Int?>("$name.set($original -> $target)") { ok, err -> set(area, target, ok, err) }
        // 요청 완료 ≠ 반영 완료. 최대 6초간 재조회로 확인
        var after: Int? = null
        for (i in 1..12) {
            Thread.sleep(500)
            after = awaitCallback<Int?>("$name.get(after #$i)") { ok, err -> get(area, ok, err) }
            if (after == target) break
        }

        runCatching { awaitCallback<Int?>("$name.restore($original)") { ok, err -> set(area, original, ok, err) } }
        assertEquals("[$name] 변경 요청 후 재조회 값", target, after)
    }
}
