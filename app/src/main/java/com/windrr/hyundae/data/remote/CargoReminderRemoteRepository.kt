package com.windrr.hyundae.data.remote

import com.windrr.hyundae.BuildConfig
import com.windrr.hyundae.data.remote.model.CargoReminderEvent
import com.windrr.hyundae.data.remote.model.CargoReminderRequest
import com.windrr.hyundae.data.remote.model.CargoReminderResponse
import java.time.OffsetDateTime
import java.util.UUID

/** 모바일 알림: 차에서 폰으로 짐 챙기기 알림 요청 */
class CargoReminderRemoteRepository(private val api: ChagokApi = ApiClient.chagokApi) {

    /** local.properties의 CHAGOK_NOTIFICATION_KEY. 비어 있으면 헤더 없이 보낸다 */
    private val notificationKey: String? = BuildConfig.CHAGOK_NOTIFICATION_KEY.takeIf { it.isNotBlank() }

    suspend fun requestCargoReminder(
        planId: String,
        event: CargoReminderEvent,
        occurredAt: OffsetDateTime = OffsetDateTime.now(),
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): ApiResult<CargoReminderResponse> = apiCall {
        api.requestCargoReminder(
            idempotencyKey = idempotencyKey,
            notificationKey = notificationKey,
            planId = planId,
            request = CargoReminderRequest(eventType = event, occurredAt = occurredAt.toString()),
        )
    }
}
