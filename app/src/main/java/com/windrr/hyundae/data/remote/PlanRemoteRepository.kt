package com.windrr.hyundae.data.remote

import com.windrr.hyundae.data.remote.model.CalculationRequest
import com.windrr.hyundae.data.remote.model.CalculationResponse
import com.windrr.hyundae.data.remote.model.PlanCreateRequest
import com.windrr.hyundae.data.remote.model.PlanResponse
import java.util.UUID

class PlanRemoteRepository(private val api: ChagokApi = ApiClient.chagokApi) {

    suspend fun createPlan(
        request: PlanCreateRequest,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): ApiResult<PlanResponse> = apiCall { api.createPlan(idempotencyKey, request) }

    suspend fun createCalculation(
        planId: String,
        request: CalculationRequest,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): ApiResult<CalculationResponse> = apiCall { api.createCalculation(idempotencyKey, planId, request) }
}
