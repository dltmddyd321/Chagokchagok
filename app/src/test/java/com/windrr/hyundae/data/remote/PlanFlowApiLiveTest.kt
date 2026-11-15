package com.windrr.hyundae.data.remote

import com.windrr.hyundae.BuildConfig
import com.windrr.hyundae.data.remote.model.CalculationCreateRequest
import com.windrr.hyundae.data.remote.model.PlanInputRequest
import com.windrr.hyundae.data.remote.model.PlanItemUpdateRequest
import com.windrr.hyundae.data.remote.model.PlanSelectionRequest
import com.windrr.hyundae.data.remote.model.PlanUpdateRequest
import com.windrr.hyundae.data.remote.model.SeatObservationInput
import com.windrr.hyundae.data.remote.model.VehicleStateInput
import com.windrr.hyundae.data.sync.PlanSyncMapper
import com.windrr.hyundae.ui.result.ArrangementLayoutMapper
import com.windrr.hyundae.ui.result.LoadingGuideBuilder
import com.windrr.hyundae.ui.result.foldDescription
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 * 실제 서버로 Pleos 내 계획 흐름 전체 확인 (앱의 Retrofit·모델·매퍼 그대로).
 * 서브 앱에서 저장한 계획이 있어야 한다: 최신 계획 1개를 쓴다.
 * 1) 동기화(GET /plans limit=1 → GET /plans/{id} → 기기 DB 모델)
 * 2) 미리 받기(선택 배치안 GET calculation → 탑뷰 매핑 → plan 기준 적재 안내)
 * 3) 변경 진행(PUT /plans/{id} → POST /plans/{id}/calculations → PUT selection → 적재 안내)
 * ⚠ 서버의 최신 계획을 실제로 바꾼다 (동승자 +1, 첫 짐 수량 +1). 테스트용 계획에만 돌릴 것.
 * ./gradlew :app:testDebugUnitTest --tests "*PlanFlowApiLiveTest*"
 */
class PlanFlowApiLiveTest {

    private val remote = PlanRemoteRepository()

    @Test
    fun pleosPlanFlow_onRealServer() = runBlocking {
        assumeTrue("서버 계정이 local.properties에 없어 건너뜀", BuildConfig.CHAGOK_API_USERNAME.isNotBlank())
        assumeTrue("CHAGOK_RUN_PLAN_FLOW=1 일 때만 (서버 계획을 바꿈)", System.getenv("CHAGOK_RUN_PLAN_FLOW") == "1")

        // 1) 동기화
        val list = (remote.recentPlans(limit = 1) as ApiResult.Success).data
        val summary = list.plans.orEmpty().first()
        val detail = (remote.plan(summary.planId) as ApiResult.Success).data
        val local = PlanSyncMapper.toLocal(summary, detail)
        println("1) 동기화: ${local.plan.name} / 인원 ${local.plan.passengerCount} / 짐 ${local.items.map { "${it.name} ${it.widthCm}x${it.depthCm}x${it.heightCm} x${it.quantity}" }} / revision ${detail.revision}")

        // 2) 미리 받기: 저장 때 고른 배치안
        val selCalc = assertNotNullAnd(detail.selectedCalculationId ?: detail.latestCalculationId, "선택 계산 없음")
        val calc = (remote.calculation(detail.planId, selCalc) as ApiResult.Success).data
        val selected = calc.arrangements.orEmpty().firstOrNull { it.arrangementId == detail.selectedArrangementId }
        assertNotNull("계산 결과에 선택 배치안(${detail.selectedArrangementId})이 없음. 받은 id=${calc.arrangements?.map { it.arrangementId }}", selected)
        val layout = ArrangementLayoutMapper.map(selected!!, calc.inputSnapshot?.items.orEmpty())
        println("2) 선택 배치안 rank${selected.rank} (${calc.arrangements?.size}개 중) / 짐 칸 ${layout.items.map { it.name }} / 접기: ${foldDescription(layout) ?: "없음"}")
        val guide = remote.loadingGuideForPlan(detail.planId, selCalc, selected.arrangementId)
        println("   적재 안내(plan 기준): $guide")
        println("   → 단계 ${LoadingGuideBuilder.build(layout, (guide as? ApiResult.Success)?.data).map { it.title }}")

        // 3) 변경 → 저장 (VM saveDraft와 같은 모양)
        val newPassengers = (detail.passengerCount ?: 1) + 1
        val items = detail.items.orEmpty()
        val update = PlanUpdateRequest(
            expectedRevision = detail.revision!!,
            plan = PlanInputRequest(
                name = detail.name,
                category = detail.category,
                memo = detail.memo,
                isFavorite = detail.isFavorite == true,
                vehicleId = detail.vehicleId!!,
                passengerCount = newPassengers,
                passengerSeats = null,
                carSeats = detail.carSeats.orEmpty(),
                retainedSeats = detail.retainedSeats.orEmpty(),
                items = items.mapIndexed { index, it ->
                    val qty = (it.quantity ?: 1) + if (index == 0) 1 else 0
                    PlanItemUpdateRequest(
                        itemId = it.itemId,
                        name = it.name ?: "짐",
                        widthCm = it.widthCm!!,
                        depthCm = it.depthCm!!,
                        heightCm = it.heightCm!!,
                        quantity = qty,
                        requiredQuantity = (it.requiredQuantity ?: 0).coerceAtMost(qty),
                        allowedOrientations = it.allowedOrientations,
                        transportState = it.transportState ?: "UNKNOWN",
                        dimensionSource = it.dimensionSource ?: "UNSPECIFIED",
                        material = it.material,
                        savedItemId = it.savedItemId,
                    )
                },
            ),
        )
        val saved = remote.updatePlan(detail.planId, update)
        println("3) PUT /plans/{id}: $saved".take(400))
        val savedPlan = (saved as ApiResult.Success).data
        println("   revision ${detail.revision} → ${savedPlan.revision}, 인원 ${savedPlan.passengerCount}, 좌석 ${savedPlan.passengerSeats}, 선택 ${savedPlan.selectedCalculationId}/${savedPlan.selectedArrangementId}")

        val recalc = remote.recalculate(
            detail.planId,
            CalculationCreateRequest(
                expectedRevision = savedPlan.revision!!,
                vehicleState = VehicleStateInput(
                    observedAt = OffsetDateTime.now().toString(),
                    seats = listOf("ROW_1_LEFT", "ROW_1_RIGHT", "ROW_2_LEFT", "ROW_2_CENTER", "ROW_2_RIGHT").map { SeatObservationInput(it, "EMPTY", "USER") },
                ),
            ),
        )
        println("   POST /plans/{id}/calculations: ${(recalc as? ApiResult.Success)?.data?.let { "calc=${it.calculationId} success=${it.isSuccess} ${it.resultCode} 배치안 ${it.arrangements?.size}" } ?: recalc}")
        val newCalc = (recalc as ApiResult.Success).data
        val best = newCalc.arrangements.orEmpty().minByOrNull { it.rank }!!
        val afterRecalc = (remote.plan(detail.planId) as ApiResult.Success).data
        println("   재계산 후 revision ${afterRecalc.revision}, latest ${afterRecalc.latestCalculationId}, 선택 ${afterRecalc.selectedCalculationId}")

        val sel = remote.selectArrangement(detail.planId, PlanSelectionRequest(savedPlan.revision!!, newCalc.calculationId!!, best.arrangementId))
        println("   PUT selection(revision ${savedPlan.revision}): $sel")
        val finalPlan = (remote.plan(detail.planId) as ApiResult.Success).data
        println("   최종 선택 ${finalPlan.selectedCalculationId}/${finalPlan.selectedArrangementId} revision ${finalPlan.revision}")
        assertEquals(newCalc.calculationId, finalPlan.selectedCalculationId)

        val newGuide = remote.loadingGuideForPlan(detail.planId, newCalc.calculationId!!, best.arrangementId)
        val newLayout = ArrangementLayoutMapper.map(best, newCalc.inputSnapshot?.items.orEmpty())
        println("   새 적재 안내: ${(newGuide as? ApiResult.Success)?.data?.let { "source=${it.source} steps=${it.steps?.size}" } ?: newGuide}")
        println("   → 단계 ${LoadingGuideBuilder.build(newLayout, (newGuide as? ApiResult.Success)?.data).map { it.title }}")
        assertTrue(newGuide !is ApiResult.NetworkError)
    }

    private fun <T> assertNotNullAnd(value: T?, message: String): T {
        assertNotNull(message, value)
        return value!!
    }
}
