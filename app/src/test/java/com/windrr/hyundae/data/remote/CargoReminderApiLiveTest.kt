package com.windrr.hyundae.data.remote

import com.windrr.hyundae.BuildConfig
import com.windrr.hyundae.data.remote.model.CargoReminderEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 실제 서버 호출 확인: POST /plans/{plan_id}/cargo-reminders.
 * 서버에 있는 계획 ID를 환경변수 CHAGOK_TEST_PLAN_ID로 주면 성공(201)을 확인하고,
 * 없으면 아무 UUID로 보내 인증·경로까지 닿는지만 본다(없는 계획이면 4xx).
 * ./gradlew :app:testDebugUnitTest --tests "*CargoReminderApiLiveTest*"
 */
class CargoReminderApiLiveTest {

    @Test
    fun requestCargoReminder_onRealServer() = runBlocking {
        assumeTrue("서버 계정이 local.properties에 없어 건너뜀", BuildConfig.CHAGOK_API_USERNAME.isNotBlank())
        val planId = System.getenv("CHAGOK_TEST_PLAN_ID") ?: "00000000-0000-4000-8000-000000000000"

        val result = CargoReminderRemoteRepository().requestCargoReminder(planId, CargoReminderEvent.DESTINATION_ARRIVED)
        println("POST plans/$planId/cargo-reminders → $result")
        assertTrue("서버에 닿지 못함: $result", result !is ApiResult.NetworkError)
        assertTrue("인증 실패: $result", (result as? ApiResult.Failure)?.httpCode != 401)
    }
}
