package com.windrr.hyundae.data.remote

import com.windrr.hyundae.data.remote.model.CalculationRequest
import com.windrr.hyundae.data.remote.model.CalculationResponse
import com.windrr.hyundae.data.remote.model.PlanCreateRequest
import com.windrr.hyundae.data.remote.model.PlanResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ChagokApi {

    @POST("api/v1/plans")
    suspend fun createPlan(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body request: PlanCreateRequest,
    ): Response<ApiResponse<PlanResponse>>

    @POST("api/v1/plans/{plan_id}/calculations")
    suspend fun createCalculation(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Path("plan_id") planId: String,
        @Body request: CalculationRequest,
    ): Response<ApiResponse<CalculationResponse>>

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    }
}
