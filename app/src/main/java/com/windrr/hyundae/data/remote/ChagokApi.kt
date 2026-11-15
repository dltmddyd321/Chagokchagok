package com.windrr.hyundae.data.remote

import com.windrr.hyundae.data.remote.model.CalculationCreateRequest
import com.windrr.hyundae.data.remote.model.PlanSelectionRequest
import com.windrr.hyundae.data.remote.model.PlanSelectionResponse
import com.windrr.hyundae.data.remote.model.CargoReminderRequest
import com.windrr.hyundae.data.remote.model.CargoReminderResponse
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
import com.windrr.hyundae.data.remote.model.ProductSearchRequest
import com.windrr.hyundae.data.remote.model.ProductSearchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ChagokApi {

    @POST("api/v1/plans")
    suspend fun createPlan(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body request: PlanCreateRequest,
    ): Response<ApiResponse<PlanResponse>>

    /** 저장된 계획 다시 계산 (짐·인원 수정 후). 응답 = 계산 결과(calculation_id·배치안) */
    @POST("api/v1/plans/{plan_id}/calculations")
    suspend fun recalculate(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Path("plan_id") planId: String,
        @Body request: CalculationCreateRequest,
    ): Response<ApiResponse<PreviewCalculation>>

    /** 사용할 배치안 정하기 */
    @PUT("api/v1/plans/{plan_id}/selection")
    suspend fun selectArrangement(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Path("plan_id") planId: String,
        @Body request: PlanSelectionRequest,
    ): Response<ApiResponse<PlanSelectionResponse>>

    /** 계획 목록 (짐은 이름·개수 요약만). sort latest|favorite */
    @GET("api/v1/plans")
    suspend fun getPlans(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String = PlanSort.LATEST.value,
        @Query("q") query: String? = null,
        @Query("category") category: String? = null,
        @Query("favorite") favorite: Boolean? = null,
    ): Response<ApiResponse<PlanListResponse>>

    /** 계획 전체 바꾸기 (짐 치수·수량·탑승 인원 수정). expected_revision 낙관적 잠금 */
    @PUT("api/v1/plans/{plan_id}")
    suspend fun updatePlan(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Path("plan_id") planId: String,
        @Body request: PlanUpdateRequest,
    ): Response<ApiResponse<PlanResponse>>

    /** 저장된 계획의 계산 결과 (배치안·좌표·짐 스냅샷). 선택한 배치안 그리기에 사용 */
    @GET("api/v1/plans/{plan_id}/calculations/{calculation_id}")
    suspend fun getCalculation(
        @Path("plan_id") planId: String,
        @Path("calculation_id") calculationId: String,
    ): Response<ApiResponse<PreviewCalculation>>

    /** 계획 상세 (짐 크기까지) */
    @GET("api/v1/plans/{plan_id}")
    suspend fun getPlan(@Path("plan_id") planId: String): Response<ApiResponse<PlanResponse>>

    /** 저장 전 배치 미리 계산 (차 안 즉석 확인) */
    @POST("api/v1/calculations/preview")
    suspend fun previewCalculation(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body request: PreviewRequest,
    ): Response<ApiResponse<PreviewResponse>>

    /** 고른 배치의 적재 안내 문구 (서버 AI). 배치 계산보다 오래 걸릴 수 있다 */
    @POST("api/v1/ai/loading-guides")
    suspend fun loadingGuide(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body request: LoadingGuideRequest,
    ): Response<ApiResponse<LoadingGuideResponse>>

    /** 제품명으로 규격 후보 찾기 (서버가 외부 웹 검색 + AI 정리). 검색할 때마다 새 Idempotency-Key */
    @POST("api/v1/products/search")
    suspend fun searchProducts(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body request: ProductSearchRequest,
    ): Response<ApiResponse<ProductSearchResponse>>

    /** 도착·문 열림 시 폰으로 짐 챙기기 알림 요청. 서버가 등록된 폰들에 FCM 발송 */
    @POST("api/v1/plans/{plan_id}/cargo-reminders")
    suspend fun requestCargoReminder(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Header(NOTIFICATION_KEY_HEADER) notificationKey: String?,
        @Path("plan_id") planId: String,
        @Body request: CargoReminderRequest,
    ): Response<ApiResponse<CargoReminderResponse>>

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"

        /** 알림 API 선택 헤더. 값이 null이면 Retrofit이 헤더를 빼고 보낸다 */
        const val NOTIFICATION_KEY_HEADER = "X-Notification-Key"
    }
}
