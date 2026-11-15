package com.windrr.hyundae.data.weather

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlin.coroutines.cancellation.CancellationException
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** 홈 날씨 카드 값 */
data class WeatherInfo(
    /** "성동구". 주소를 못 찾으면 null */
    val district: String?,
    /** "흐림" */
    val condition: String,
    val conditionType: WeatherType,
    /** 섭씨, 반올림 */
    val temperatureC: Int,
)

enum class WeatherType { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, SNOW, THUNDER }

/**
 * 현재 위치(Pleos FusedLocation) → Open-Meteo 현재 날씨.
 * Open-Meteo는 API 키 없이 쓰는 무료 날씨 API (https://open-meteo.com).
 * 위치를 못 받으면 데모 기본 위치(서울 성동구)로 조회한다.
 */
class WeatherRepository(private val context: Context) {

    suspend fun current(): WeatherInfo? = withContext(Dispatchers.IO) {
        val (lat, lon) = currentLocation() ?: DEFAULT_LOCATION
        runCatching {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto",
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val current = JSONObject(body).getJSONObject("current")
            val (type, label) = describe(current.getInt("weather_code"))
            WeatherInfo(
                district = district(lat, lon),
                condition = label,
                conditionType = type,
                temperatureC = current.getDouble("temperature_2m").roundToInt(),
            )
        }.onFailure { Log.w(TAG, "날씨 조회 실패", it) }.getOrNull()
    }

    /** Pleos 통합 위치 (최대 3초 기다림) */
    private suspend fun currentLocation(): Pair<Double, Double>? = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
        // 처음 설치하면 권한 창보다 날씨 조회가 먼저 돈다 → 권한 없으면 기본 위치 (SDK가 SecurityException을 던져 앱이 죽음)
        if (ContextCompat.checkSelfPermission(context, LOCATION_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return@withTimeoutOrNull null
        }
        val fused = runCatching { FusedLocation(context).apply { initialize() } }.getOrNull() ?: return@withTimeoutOrNull null
        try {
            runCatching<Pair<Double, Double>?> { suspendCancellableCoroutine { cont ->
                fused.getLastKnownLocation(object : FusedLocationListener {
                    override fun onChanged(location: LocationData?) {
                        if (cont.isActive) cont.resume(location?.let { it.latitude to it.longitude })
                    }

                    override fun onError(exception: Exception) {
                        Log.w(TAG, "위치 조회 실패", exception)
                        if (cont.isActive) cont.resume(null)
                    }
                })
            } }.onFailure { if (it is CancellationException) throw it; Log.w(TAG, "위치 조회 실패", it) }.getOrNull()
        } finally {
            runCatching { fused.release() }
        }
    }

    /** 좌표 → 구 이름 (기기 지오코더, 없으면 null) */
    @Suppress("DEPRECATION")
    private fun district(lat: Double, lon: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        val address = Geocoder(context, Locale.KOREA).getFromLocation(lat, lon, 1)?.firstOrNull() ?: return null
        address.subLocality ?: address.locality ?: address.adminArea
    }.getOrNull()

    /** WMO 날씨 코드 → 화면 문구 */
    private fun describe(code: Int): Pair<WeatherType, String> = when (code) {
        0 -> WeatherType.CLEAR to "맑음"
        1, 2 -> WeatherType.PARTLY_CLOUDY to "구름 조금"
        3 -> WeatherType.CLOUDY to "흐림"
        45, 48 -> WeatherType.FOG to "안개"
        in 51..67, in 80..82 -> WeatherType.RAIN to "비"
        in 71..77, 85, 86 -> WeatherType.SNOW to "눈"
        in 95..99 -> WeatherType.THUNDER to "뇌우"
        else -> WeatherType.CLOUDY to "흐림"
    }

    private companion object {
        const val TAG = "WeatherRepository"
        const val TIMEOUT_MS = 5_000
        const val LOCATION_TIMEOUT_MS = 3_000L

        /** 위치를 못 받을 때 데모 기본 위치: 서울 성동구 */
        val DEFAULT_LOCATION = 37.5634 to 127.0369
    }
}

private const val LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"
