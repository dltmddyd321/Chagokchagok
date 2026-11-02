package com.windrr.hyundae.sdk

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NaviHelperSdkTest {

    private lateinit var naviHelper: NaviHelper
    private var listener: NaviHelperEventListener? = null

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
        listener?.let(naviHelper::removeListener)
        naviHelper.release()
    }

    @Test
    fun getCurrentLocationInfo() {
        awaitCallback<String>("NaviHelper.getCurrentLocationInfo") { ok, err ->
            listener = object : NaviHelperEventListener {
                override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) =
                    ok("${currentLocationInfo.latitude}, ${currentLocationInfo.longitude} ${currentLocationInfo.address}")

                override fun onError(errorCode: NaviErrorCode) = err(errorCode.toString())
            }.also(naviHelper::addListener)
            naviHelper.getCurrentLocationInfo()
        }
    }

    @Test
    fun getRouteStateInfo() {
        awaitCallback<String>("NaviHelper.getRouteStateInfo") { ok, err ->
            listener = object : NaviHelperEventListener {
                override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) =
                    ok("naviMode=${routeStateInfo.naviMode}, routeOption=${routeStateInfo.routeOption}")

                override fun onError(errorCode: NaviErrorCode) = err(errorCode.toString())
            }.also(naviHelper::addListener)
            naviHelper.getRouteStateInfo()
        }
    }
}
