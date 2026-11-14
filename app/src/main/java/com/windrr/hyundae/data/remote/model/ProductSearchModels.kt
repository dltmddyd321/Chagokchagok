package com.windrr.hyundae.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * POST /api/v1/products/search 요청.
 * 서버가 외부 웹에서 제품 규격을 찾아 AI로 후보를 정리한다 (서버 DB 조회 아님).
 * transport_states를 생략하면 PACKAGED·ASSEMBLED·FOLDED 셋 다 찾는다.
 */
data class ProductSearchRequest(
    @SerializedName("query") val query: String,
    @SerializedName("transport_states") val transportStates: List<String>? = null,
)

data class ProductSearchResponse(
    @SerializedName("candidates") val candidates: List<ProductCandidateResponse>?,
)

/**
 * 제품 규격 후보. 같은 제품도 운반 상태(포장·조립·접힘)마다 따로 온다.
 * 확인 못 한 치수는 null (0으로 채우지 않음). 항상 사용자 확인이 필요하다.
 */
data class ProductCandidateResponse(
    @SerializedName("candidate_id") val candidateId: String,
    @SerializedName("name") val name: String,
    @SerializedName("model") val model: String?,
    @SerializedName("variant") val variant: String?,
    /** PACKAGED / ASSEMBLED / FOLDED */
    @SerializedName("transport_state") val transportState: String,
    @SerializedName("width_cm") val widthCm: Double?,
    @SerializedName("depth_cm") val depthCm: Double?,
    @SerializedName("height_cm") val heightCm: Double?,
    @SerializedName("sources") val sources: List<ProductSourceResponse>?,
    @SerializedName("requires_confirmation") val requiresConfirmation: Boolean?,
)

/** 서버가 실제로 열어 확인한 출처 */
data class ProductSourceResponse(
    @SerializedName("url") val url: String,
    @SerializedName("title") val title: String,
)
