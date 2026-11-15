package com.windrr.hyundae.data.remote.model

import com.google.gson.annotations.SerializedName

data class PlanCreateRequest(
    @SerializedName("vehicle_id") val vehicleId: String,
    @SerializedName("passenger_count") val passengerCount: Int,
    @SerializedName("car_seats") val carSeats: List<String> = emptyList(),
    @SerializedName("retained_seats") val retainedSeats: List<String> = emptyList(),
    @SerializedName("items") val items: List<PlanItemRequest>,
)

data class PlanItemRequest(
    @SerializedName("name") val name: String,
    @SerializedName("width_cm") val widthCm: Double,
    @SerializedName("depth_cm") val depthCm: Double,
    @SerializedName("height_cm") val heightCm: Double,
    @SerializedName("quantity") val quantity: Int,
)

data class PlanResponse(
    @SerializedName("plan_id") val planId: String,
    @SerializedName("name") val name: String? = null,
    /** TRAVEL / LEISURE / MOVING. null이면 앱은 여행으로 본다 */
    @SerializedName("category") val category: String? = null,
    @SerializedName("memo") val memo: String? = null,
    @SerializedName("is_favorite") val isFavorite: Boolean? = null,
    @SerializedName("vehicle_id") val vehicleId: String?,
    @SerializedName("passenger_count") val passengerCount: Int?,
    @SerializedName("passenger_seats") val passengerSeats: List<String>? = null,
    /** 낙관적 잠금 버전. 수정 요청에 expected_revision으로 */
    @SerializedName("revision") val revision: Int? = null,
    @SerializedName("car_seats") val carSeats: List<String>?,
    @SerializedName("retained_seats") val retainedSeats: List<String>?,
    @SerializedName("items") val items: List<PlanItemResponse>?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("latest_calculation_id") val latestCalculationId: String?,
)

data class PlanItemResponse(
    @SerializedName("item_id") val itemId: String,
    @SerializedName("name") val name: String?,
    @SerializedName("width_cm") val widthCm: Double?,
    @SerializedName("depth_cm") val depthCm: Double?,
    @SerializedName("height_cm") val heightCm: Double?,
    @SerializedName("quantity") val quantity: Int?,
    @SerializedName("required_quantity") val requiredQuantity: Int? = null,
    @SerializedName("allowed_orientations") val allowedOrientations: List<String>? = null,
    /** PACKAGED / ASSEMBLED / FOLDED / OTHER / UNKNOWN */
    @SerializedName("transport_state") val transportState: String? = null,
    @SerializedName("dimension_source") val dimensionSource: String? = null,
    @SerializedName("material") val material: String? = null,
    @SerializedName("saved_item_id") val savedItemId: String? = null,
)

/** PUT /api/v1/plans/{plan_id}: 계획 전체 바꾸기 (expected_revision 필수) */
data class PlanUpdateRequest(
    @SerializedName("expected_revision") val expectedRevision: Int,
    @SerializedName("plan") val plan: PlanInputRequest,
)

data class PlanInputRequest(
    @SerializedName("name") val name: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("memo") val memo: String?,
    @SerializedName("is_favorite") val isFavorite: Boolean,
    @SerializedName("vehicle_id") val vehicleId: String,
    @SerializedName("passenger_count") val passengerCount: Int,
    /** null이면 서버가 인원 수에 맞춰 자리를 정한다 */
    @SerializedName("passenger_seats") val passengerSeats: List<String>?,
    @SerializedName("car_seats") val carSeats: List<String>,
    @SerializedName("retained_seats") val retainedSeats: List<String>,
    @SerializedName("items") val items: List<PlanItemUpdateRequest>,
)

/** 기존 짐은 item_id를 그대로 보내 같은 짐으로 유지 */
data class PlanItemUpdateRequest(
    @SerializedName("item_id") val itemId: String?,
    @SerializedName("name") val name: String,
    @SerializedName("width_cm") val widthCm: Double,
    @SerializedName("depth_cm") val depthCm: Double,
    @SerializedName("height_cm") val heightCm: Double,
    @SerializedName("quantity") val quantity: Int,
    @SerializedName("required_quantity") val requiredQuantity: Int = 0,
    @SerializedName("allowed_orientations") val allowedOrientations: List<String>? = null,
    @SerializedName("transport_state") val transportState: String = "UNKNOWN",
    @SerializedName("dimension_source") val dimensionSource: String = "UNSPECIFIED",
    @SerializedName("material") val material: String? = null,
    @SerializedName("saved_item_id") val savedItemId: String? = null,
)
