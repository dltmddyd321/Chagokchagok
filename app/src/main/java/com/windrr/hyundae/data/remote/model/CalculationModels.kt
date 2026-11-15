package com.windrr.hyundae.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * POST /api/v1/plans/{plan_id}/calculations: 저장된 계획을 다시 계산 (내 계획에서 짐·인원을 바꾼 뒤).
 * 응답은 미리보기와 같은 CalculationData → [PreviewCalculation].
 */
data class CalculationCreateRequest(
    /** 방금 저장한 계획 revision */
    @SerializedName("expected_revision") val expectedRevision: Int,
    @SerializedName("packing_mode") val packingMode: String = "REPACK_ALL",
    @SerializedName("allow_exclusions") val allowExclusions: Boolean = false,
    /** 좌석·접은 좌석을 쓰려면 현재 비어 있는 좌석 (전 좌석 비어 있음, 사용자 입력) */
    @SerializedName("vehicle_state") val vehicleState: VehicleStateInput? = null,
)

/** PUT /api/v1/plans/{plan_id}/selection: 사용할 배치안 */
data class PlanSelectionRequest(
    @SerializedName("expected_revision") val expectedRevision: Int,
    @SerializedName("calculation_id") val calculationId: String,
    @SerializedName("arrangement_id") val arrangementId: String,
)

data class PlanSelectionResponse(
    @SerializedName("plan_id") val planId: String,
    @SerializedName("revision") val revision: Int,
    @SerializedName("selected_calculation_id") val selectedCalculationId: String,
    @SerializedName("selected_arrangement_id") val selectedArrangementId: String,
)
