package com.windrr.hyundae.data.remote.model

import com.google.gson.annotations.SerializedName

/** 짐 챙기기 알림을 보내는 계기 */
enum class CargoReminderEvent {
    @SerializedName("DESTINATION_ARRIVED") DESTINATION_ARRIVED,
    @SerializedName("DOOR_OPENED") DOOR_OPENED,
}

/** POST /api/v1/plans/{plan_id}/cargo-reminders 요청. occurred_at은 ISO-8601(오프셋 포함) */
data class CargoReminderRequest(
    @SerializedName("event_type") val eventType: CargoReminderEvent,
    @SerializedName("occurred_at") val occurredAt: String,
)

data class CargoReminderResponse(
    @SerializedName("notification_id") val notificationId: String,
    @SerializedName("plan_id") val planId: String,
    @SerializedName("event_type") val eventType: String?,
    /** ACCEPTED, PARTIAL, RETRYABLE, FAILED, UNKNOWN, NO_DEVICES(등록된 폰 없음) */
    @SerializedName("status") val status: String?,
    @SerializedName("accepted_count") val acceptedCount: Int?,
    @SerializedName("deliveries") val deliveries: List<DeliveryResponse>?,
)

data class DeliveryResponse(
    @SerializedName("device_id") val deviceId: String,
    /** ACCEPTED, RETRYABLE, FAILED, UNKNOWN, SKIPPED */
    @SerializedName("status") val status: String?,
    @SerializedName("error_code") val errorCode: String?,
    @SerializedName("retry_at") val retryAt: String?,
)
