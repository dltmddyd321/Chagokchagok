package com.windrr.hyundae.sdk

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 콘솔상 Coming Soon SDK. FUSED_LOCATION은 dangerous 퍼미션이라 앱에서는 런타임 요청 필요 */
@RunWith(AndroidJUnit4::class)
class FusedLocationSdkTest {

    @get:Rule
    val grantPermission: GrantPermissionRule = GrantPermissionRule.grant("pleos.car.permission.FUSED_LOCATION")

    private lateinit var fusedLocation: FusedLocation

    @Before
    fun setUp() = onMain {
        fusedLocation = FusedLocation(targetContext).apply { initialize() }
    }

    @After
    fun tearDown() = onMain { fusedLocation.release() }

    @Test
    fun isSupported() {
        Thread.sleep(1_000)
        assertTrue("FusedLocation.isSupported() == false", fusedLocation.isSupported())
    }

    @Test
    fun getLastKnownLocation() {
        awaitWithRetry<String>("FusedLocation.getLastKnownLocation") { ok, err ->
            fusedLocation.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    if (location == null) err("location == null")
                    else ok("${location.latitude}, ${location.longitude} heading=${location.heading}")
                }

                override fun onError(exception: Exception) = err(exception.toString())
            })
        }
    }
}
