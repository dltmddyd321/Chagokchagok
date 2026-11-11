package com.windrr.hyundae.sdk

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteOverviewShowInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 픽업 길안내 화면 기획용 탐색 테스트.
 * ① 길안내 시작 없이 경로 정보(거리·도착시간·경로상 지점·혼잡 구간)를 받을 수 있는지
 * ② 길안내 시작 후 기본 내비의 "전체 경로 보기" 화면을 띄울 수 있는지 (화면 캡처로 확인)
 */
@RunWith(AndroidJUnit4::class)
class NaviPreviewProbeTest {

    private lateinit var naviHelper: NaviHelper
    private val listeners = mutableListOf<NaviHelperEventListener>()

    @Before
    fun setUp() {
        awaitCallback<Unit>("NaviHelper.initialize") { ok, err ->
            onMain {
                naviHelper = NaviHelper(targetContext)
                naviHelper.initialize({ ok(Unit) }, { err(it.toString()) })
            }
        }
    }

    @After
    fun tearDown() = onMain {
        listeners.forEach(naviHelper::removeListener)
        runCatching { naviHelper.cancelRoute() }
        naviHelper.release()
    }

    @Test
    fun requestTrip_routePreviewData() {
        awaitCallback<TripInfo>("NaviHelper.requestTrip", timeoutSec = 20) { ok, err ->
            listen(object : NaviHelperEventListener {
                override fun onTripInfo(tripInfo: TripInfo) = ok(tripInfo)
                override fun onError(errorCode: NaviErrorCode) = err(errorCode.toString())
            })
            naviHelper.requestTrip(TripRequest(PANGYO_LAT, PANGYO_LNG, "", "판교역", null))
        }.also { trip ->
            Log.i(TAG, "[Trip] distance=${trip.distance} eta=${trip.eta} socAtDest=${trip.estimatedSocAtDest}")
            trip.summary?.routePoints?.forEach {
                Log.i(TAG, "[Trip.routePoint] ${it.pointType} ${it.poiName} ratio=${it.ratioOnRoute} (${it.latitude}, ${it.longitude})")
            }
            Log.i(TAG, "[Trip.traffic] ${trip.summary?.trafficConditions?.size ?: 0} segments: " +
                trip.summary?.trafficConditions?.take(5)?.joinToString { "${it.congestionType}:${it.startRatio}-${it.endRatio}" })
        }
    }

    @Test
    fun startRoute_thenShowOverviewScreen() {
        awaitCallback<String>("NaviHelper.requestRoute", timeoutSec = 30) { ok, err ->
            listen(object : NaviHelperEventListener {
                override fun onRouteStarted(routeStartInfo: RouteStartInfo) =
                    ok("${routeStartInfo.name} distance=${routeStartInfo.distance} duration=${routeStartInfo.duration}")
                override fun onError(errorCode: NaviErrorCode) = err(errorCode.toString())
            })
            naviHelper.requestRoute(
                RouteInfo(latitude = PANGYO_LAT, longitude = PANGYO_LNG, poiName = "판교역", poiId = "", address = "", poiSubId = ""),
            )
        }
        Thread.sleep(3_000)
        capture("1_route_started")

        onMain { naviHelper.requestNavigationScreen(NavigationScreenRequest.OverviewRoute(RouteOverviewShowInfo(10_000))) }
        Thread.sleep(3_000)
        capture("2_overview_route")
    }

    private fun listen(listener: NaviHelperEventListener) {
        listeners += listener
        naviHelper.addListener(listener)
    }

    private fun capture(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val file = File(targetContext.getExternalFilesDir(null), "navi_$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i(TAG, "[Capture] ${file.absolutePath}")
    }

    private companion object {
        const val PANGYO_LAT = 37.3947
        const val PANGYO_LNG = 127.1112
    }
}
