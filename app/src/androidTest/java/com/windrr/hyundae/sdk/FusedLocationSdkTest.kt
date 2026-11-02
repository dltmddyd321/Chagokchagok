package com.windrr.hyundae.sdk

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 콘솔상 Coming Soon SDK — 실패해도 이상하지 않음 */
@RunWith(AndroidJUnit4::class)
class FusedLocationSdkTest {

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
