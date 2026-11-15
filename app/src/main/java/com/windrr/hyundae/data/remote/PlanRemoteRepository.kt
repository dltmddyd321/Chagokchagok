package com.windrr.hyundae.data.remote

import com.windrr.hyundae.data.remote.model.CalculationRequest
import com.windrr.hyundae.data.remote.model.CalculationResponse
import com.windrr.hyundae.data.remote.model.PlanCreateRequest
import com.windrr.hyundae.data.remote.model.PlanListResponse
import com.windrr.hyundae.data.remote.model.PlanResponse
import com.windrr.hyundae.data.remote.model.LoadingGuideRequest
import com.windrr.hyundae.data.remote.model.LoadingGuideResponse
import com.windrr.hyundae.data.remote.model.PlanSort
import com.windrr.hyundae.data.remote.model.PlanUpdateRequest
import com.windrr.hyundae.data.remote.model.PreviewCalculation
import com.windrr.hyundae.data.remote.model.PreviewRequest
import com.windrr.hyundae.data.remote.model.PreviewResponse
import java.util.UUID

class PlanRemoteRepository(private val api: ChagokApi = ApiClient.chagokApi) {

    /** 최근 등록 순 계획 목록 */
    suspend fun recentPlans(limit: Int = 20): ApiResult<PlanListResponse> =
        apiCall { api.getPlans(limit = limit, sort = PlanSort.LATEST.value) }

    /** 계획 전체 바꾸기. 수정할 때마다 새 Idempotency-Key */
    suspend fun updatePlan(
        planId: String,
        request: PlanUpdateRequest,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): ApiResult<PlanResponse> = apiCall { api.updatePlan(idempotencyKey, planId, request) }

    /** 저장된 계획의 계산 결과 */
    suspend fun calculation(planId: String, calculationId: String): ApiResult<PreviewCalculation> =
        apiCall { api.getCalculation(planId, calculationId) }

    /** 계획 상세 (짐 크기 포함) */
    suspend fun plan(planId: String): ApiResult<PlanResponse> = apiCall { api.getPlan(planId) }

    suspend fun createPlan(
        request: PlanCreateRequest,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): ApiResult<PlanResponse> = apiCall { api.createPlan(idempotencyKey, request) }

    suspend fun createCalculation(
        planId: String,
        request: CalculationRequest,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): ApiResult<CalculationResponse> = apiCall { api.createCalculation(idempotencyKey, planId, request) }

    /** 배치 미리 계산. 재시도엔 같은 [idempotencyKey], 새 계산은 새 키 */
    suspend fun previewCalculation(request: PreviewRequest, idempotencyKey: String): ApiResult<PreviewResponse> =
        apiCall { api.previewCalculation(idempotencyKey, request) }

    /** 배치 하나의 적재 안내. 배치마다 새 Idempotency-Key */
    suspend fun loadingGuide(
        previewId: String,
        arrangementId: String,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): ApiResult<LoadingGuideResponse> =
        apiCall { api.loadingGuide(idempotencyKey, LoadingGuideRequest(previewId, arrangementId)) }
}
