package com.windrr.hyundae.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * POST /api/v1/ai/loading-guides 요청.
 * 새 계획(미리보기)은 preview_id + arrangement_id, 저장된 계획은 plan_id + calculation_id + arrangement_id. 안 쓰는 칸은 null(전송 안 함)
 */
data class LoadingGuideRequest(
    @SerializedName("preview_id") val previewId: String? = null,
    @SerializedName("arrangement_id") val arrangementId: String,
    @SerializedName("plan_id") val planId: String? = null,
    @SerializedName("calculation_id") val calculationId: String? = null,
)

data class LoadingGuideResponse(
    @SerializedName("arrangement_id") val arrangementId: String,
    /** AI / FALLBACK (AI를 못 쓰면 기본 문구, steps가 비어 올 수 있음) */
    @SerializedName("source") val source: String?,
    @SerializedName("reply") val reply: String?,
    @SerializedName("steps") val steps: List<LoadingGuideStepResponse>?,
)

/** 안내 한 단계. placement_id로 배치의 짐과 이어진다 */
data class LoadingGuideStepResponse(
    @SerializedName("order") val order: Int,
    @SerializedName("placement_id") val placementId: String,
    @SerializedName("text") val text: String,
)
