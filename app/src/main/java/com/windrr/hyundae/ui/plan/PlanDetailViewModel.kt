package com.windrr.hyundae.ui.plan

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.windrr.hyundae.data.local.AppDatabase
import com.windrr.hyundae.data.local.entity.PlanItemEntity
import com.windrr.hyundae.data.local.relation.PlanWithDetails
import com.windrr.hyundae.data.remote.ApiResult
import com.windrr.hyundae.data.remote.PlanRemoteRepository
import com.windrr.hyundae.data.remote.model.CalculationCreateRequest
import com.windrr.hyundae.data.remote.model.PlanInputRequest
import com.windrr.hyundae.data.remote.model.PlanSelectionRequest
import com.windrr.hyundae.data.remote.model.PreviewCalculation
import com.windrr.hyundae.data.remote.model.SeatObservationInput
import com.windrr.hyundae.data.remote.model.VehicleStateInput
import com.windrr.hyundae.data.remote.model.PlanItemUpdateRequest
import com.windrr.hyundae.data.remote.model.PlanResponse
import com.windrr.hyundae.data.remote.model.PlanUpdateRequest
import com.windrr.hyundae.ui.loadcheck.LoadCheckIntent
import com.windrr.hyundae.ui.loadcheck.LoadCheckUiState
import com.windrr.hyundae.ui.loadcheck.LoadItem
import com.windrr.hyundae.ui.loadcheck.ManualInputState
import com.windrr.hyundae.ui.result.ArrangementOptionUi
import com.windrr.hyundae.ui.result.GuideStepUi
import com.windrr.hyundae.ui.result.LoadCompletion
import com.windrr.hyundae.ui.result.LoadingGuideBuilder
import com.windrr.hyundae.ui.result.seatGroupName
import com.windrr.hyundae.ui.result.ArrangementLayoutMapper
import com.windrr.hyundae.ui.result.ArrangementLayoutUi
import com.windrr.hyundae.ui.result.foldDescription
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/** 내 계획 상세의 짐 한 줄 */
data class PlanItemUi(
    val itemId: String,
    val name: String,
    val quantity: Int,
    val widthCm: Int,
    val depthCm: Int,
    val heightCm: Int,
    /** "접은 상태" 등 (서버 상세에서). 모르면 null */
    val stateLabel: String?,
) {
    /** "1개 · 접은 상태" / "3개 · 개당 크기" */
    val countLabel: String
        get() = listOfNotNull("${quantity}개", stateLabel ?: if (quantity > 1) "개당 크기" else null).joinToString(" · ")
}

/** 배치도 미리보기 창 */
sealed interface PlanPreviewState {
    data object Loading : PlanPreviewState

    data class Ready(val layout: ArrangementLayoutUi, val foldText: String?) : PlanPreviewState

    data class Message(val text: String) : PlanPreviewState
}

/** 계획 진행하기 이후 화면 (null = 상세 화면) */
sealed interface PlanRunState {
    /** 저장·재계산·안내 받는 중 (전체 로딩) */
    data class Preparing(val text: String) : PlanRunState

    /** 적재 가이드. steps = null 이면 안내를 만드는 중 */
    data class Guide(val option: ArrangementOptionUi, val steps: List<GuideStepUi>?) : PlanRunState

    data class Complete(val option: ArrangementOptionUi, val items: List<LoadItem>, val completion: LoadCompletion) : PlanRunState
}

data class PlanDetailUiState(
    val loading: Boolean = true,
    val planId: String? = null,
    val name: String = "",
    val items: List<PlanItemUi> = emptyList(),
    /** 운전자 제외 */
    val companions: Int = 0,
    /** 짐 치수 수정 창 */
    val editing: ManualInputState? = null,
    /** 서버 저장 중 */
    val saving: Boolean = false,
    /** 배치도 미리보기 (null = 닫힘) */
    val preview: PlanPreviewState? = null,
    /** 짐·인원을 바꿨는데 아직 저장 안 함 → 진행 전에 확인 창 */
    val dirty: Boolean = false,
    /** "변경한 계획으로 진행할까요?" 창 */
    val confirmProceed: Boolean = false,
    /** 계획 진행 (가이드·완료) */
    val run: PlanRunState? = null,
    val message: String? = null,
) {
    val totalPassengers: Int get() = 1 + companions
    val itemCount: Int get() = items.sumOf { it.quantity }

    companion object {
        const val MAX_COMPANIONS = 4
    }
}

/**
 * 내 계획 (Pleos에 선택된 최신 계획) 상세.
 * - 화면은 기기 DB(동기화된 최신 계획)로 바로 그리고, 서버 상세(GET /plans/{id})로 운반 상태·revision을 채운다.
 * - 짐 수정·동승자 변경은 바로 저장하지 않고 화면에만 들고 있는다(변경본).
 * - 계획 진행하기
 *   - 바뀐 게 없으면: 저장 때 골라 둔 배치안 + 적재 안내를 미리 받아 둔 것으로 곧바로 가이드 (기다림 없음).
 *   - 바뀌었으면: 확인 창 → PUT /plans/{id} → POST /plans/{id}/calculations(재계산) → 1순위 배치안 선택(PUT selection)
 *     → 적재 안내 → 가이드. 사용자 동작 한 번에 요청 한 번씩, 자동 재요청 없음.
 */
class PlanDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val remote = PlanRemoteRepository()

    private val _state = MutableStateFlow(PlanDetailUiState())
    val state: StateFlow<PlanDetailUiState> = _state.asStateFlow()

    private var local: PlanWithDetails? = null
    private var server: PlanResponse? = null

    /** 변경본 (null = 기기 DB 그대로) */
    private var draftItems: List<PlanItemEntity>? = null
    private var draftPassengers: Int? = null

    /** 저장은 했는데 재계산이 아직 안 됨 (재계산 실패 후) → 다음 진행 때 재계산 */
    private var needsRecalc = false

    /** 미리 받아 둔 선택 배치안 + 적재 안내 */
    private data class Prepared(val key: String, val option: ArrangementOptionUi, val steps: List<GuideStepUi>?)

    private var prepared: Prepared? = null
    private var prepareJob: Job? = null

    init {
        viewModelScope.launch {
            db.planDao().observePlans().collect { plans ->
                val latest = plans.firstOrNull()
                val changedPlan = latest?.plan?.planId != local?.plan?.planId
                val changedContent = latest != local
                local = latest
                if (changedPlan) {
                    server = null
                    clearDraft()
                    prepared = null
                    prepareJob?.cancel()
                    needsRecalc = false
                }
                render()
                // 다른 기기에서 계획이 바뀌어 동기화됐을 수도 있으니 내용이 바뀌면 서버 상세를 다시 본다 (선택 배치안 갱신)
                if (latest != null && (changedPlan || (changedContent && !_state.value.dirty && _state.value.run == null))) loadServer(latest.plan.planId)
            }
        }
    }

    private fun loadServer(planId: String) {
        viewModelScope.launch {
            when (val result = remote.plan(planId)) {
                is ApiResult.Success -> if (local?.plan?.planId == planId) {
                    server = result.data
                    render()
                    prefetch()
                    if (_state.value.run is PlanRunState.Preparing && !_state.value.saving) awaitPrepared()
                }
                else -> {
                    Log.w(TAG, "계획 상세 조회 실패: $result")
                    _state.update { if (it.run is PlanRunState.Preparing && !it.saving) it.copy(run = null, message = "계획 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.") else it }
                }
            }
        }
    }

    private fun clearDraft() {
        draftItems = null
        draftPassengers = null
    }

    private fun render() {
        val details = local
        val serverItems = server?.items.orEmpty().associateBy { it.itemId }
        val items = draftItems ?: details?.items.orEmpty()
        val passengers = draftPassengers ?: details?.plan?.passengerCount ?: 1
        _state.update { s ->
            if (details == null) {
                PlanDetailUiState(loading = false)
            } else {
                s.copy(
                    loading = false,
                    planId = details.plan.planId,
                    name = details.plan.name.ifBlank { "이름 없는 계획" },
                    companions = (passengers - 1).coerceIn(0, PlanDetailUiState.MAX_COMPANIONS),
                    items = items.map { item ->
                        PlanItemUi(
                            itemId = item.itemId,
                            name = item.name,
                            quantity = item.quantity,
                            widthCm = item.widthCm,
                            depthCm = item.depthCm,
                            heightCm = item.heightCm,
                            stateLabel = stateLabel(serverItems[item.itemId]?.transportState),
                        )
                    },
                    dirty = isDirty(),
                )
            }
        }
    }

    private fun isDirty(): Boolean {
        val details = local ?: return false
        val itemsChanged = draftItems != null && draftItems != details.items
        val passengersChanged = draftPassengers != null && draftPassengers != details.plan.passengerCount
        return itemsChanged || passengersChanged
    }

    // ── 동승자 ──
    fun changeCompanions(delta: Int) {
        val s = _state.value
        val next = (s.companions + delta).coerceIn(0, PlanDetailUiState.MAX_COMPANIONS)
        if (next == s.companions || s.saving) return
        draftPassengers = next + 1
        render()
    }
    // ── 짐 수정 창 ──
    fun openEdit(itemId: String) {
        val item = _state.value.items.firstOrNull { it.itemId == itemId } ?: return
        _state.update {
            it.copy(
                editing = ManualInputState(
                    name = item.name,
                    quantity = item.quantity,
                    width = item.widthCm.toString(),
                    depth = item.depthCm.toString(),
                    height = item.heightCm.toString(),
                    editingKey = item.itemId,
                    stateLabel = item.stateLabel,
                    editNameAndQuantity = true,
                ),
            )
        }
    }

    fun onEditIntent(intent: LoadCheckIntent) {
        when (intent) {
            LoadCheckIntent.CloseManualInput -> _state.update { it.copy(editing = null) }
            LoadCheckIntent.ConfirmManualInput -> confirmEdit()
            else -> _state.update { s -> s.editing?.let { s.copy(editing = it.reduce(intent, maxQuantity = MAX_ITEM_QUANTITY)) } ?: s }
        }
    }

    private fun confirmEdit() {
        val input = _state.value.editing ?: return
        val key = input.editingKey ?: return
        val edited = input.toItem(key, fallbackName = "짐") ?: return
        val items = currentItems().map {
            if (it.itemId == key) it.copy(name = edited.name, quantity = edited.quantity, widthCm = edited.widthCm, depthCm = edited.depthCm, heightCm = edited.heightCm) else it
        }
        draftItems = items
        _state.update { it.copy(editing = null) }
        render()
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ── 배치도 미리보기: 저장할 때 고른 배치안(selected_calculation_id / selected_arrangement_id)을 받아 그린다 ──
    fun openPreview() {
        val planId = _state.value.planId ?: return
        prepared?.takeIf { !needsRecalc }?.let { ready ->
            _state.update { it.copy(preview = PlanPreviewState.Ready(ready.option.layout, foldDescription(ready.option.layout))) }
            return
        }
        _state.update { it.copy(preview = PlanPreviewState.Loading) }
        viewModelScope.launch {
            val next = loadPreview(planId)
            _state.update { if (it.preview == PlanPreviewState.Loading) it.copy(preview = next) else it }
        }
    }

    fun closePreview() = _state.update { it.copy(preview = null) }

    private suspend fun loadPreview(planId: String): PlanPreviewState {
        // 선택 배치는 최신 계획 상세에서 확인 (다른 기기에서 바뀌었을 수 있음)
        val plan = when (val result = remote.plan(planId)) {
            is ApiResult.Success -> result.data.also { server = it }
            else -> return PlanPreviewState.Message("계획 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.")
        }
        val calculationId = plan.selectedCalculationId ?: plan.latestCalculationId
            ?: return PlanPreviewState.Message("이 계획에는 저장된 배치안이 없어요.")
        val calculation = when (val result = remote.calculation(planId, calculationId)) {
            is ApiResult.Success -> result.data
            else -> {
                Log.w(TAG, "계산 결과 조회 실패: $result")
                return PlanPreviewState.Message("배치안을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.")
            }
        }
        val arrangements = calculation.arrangements.orEmpty()
        val arrangement = plan.selectedArrangementId?.let { id -> arrangements.firstOrNull { it.arrangementId == id } }
            ?: arrangements.minByOrNull { it.rank }.takeIf { plan.selectedArrangementId == null }
            ?: return PlanPreviewState.Message("선택한 배치안을 찾지 못했어요.")
        val layout = ArrangementLayoutMapper.map(arrangement, calculation.inputSnapshot?.items.orEmpty())
        return PlanPreviewState.Ready(layout, foldDescription(layout))
    }

    private fun currentItems(): List<PlanItemEntity> = draftItems ?: local?.items.orEmpty()

    // ── 미리 받아 두기: 저장 때 고른 배치안 + 적재 안내 ──
    private fun prefetch() {
        val plan = server ?: return
        if (needsRecalc) return
        val calculationId = plan.selectedCalculationId ?: plan.latestCalculationId ?: return
        val key = "$calculationId/${plan.selectedArrangementId}"
        if (prepared?.key == key || prepareJob?.isActive == true) return
        prepareJob = viewModelScope.launch {
            val calculation = when (val result = remote.calculation(plan.planId, calculationId)) {
                is ApiResult.Success -> result.data
                else -> { Log.w(TAG, "선택 배치안 미리 받기 실패: $result"); return@launch }
            }
            val arrangements = calculation.arrangements.orEmpty()
            val arrangement = plan.selectedArrangementId?.let { id -> arrangements.firstOrNull { it.arrangementId == id } }
                ?: arrangements.minByOrNull { it.rank }.takeIf { plan.selectedArrangementId == null }
                ?: run { Log.w(TAG, "선택 배치안 없음 $key"); return@launch }
            val option = option(arrangement, calculation, plan.passengerCount ?: 1)
            prepared = Prepared(key, option, steps = null)
            val steps = guideSteps(plan.planId, calculationId, option)
            prepared = Prepared(key, option, steps)
            // 가이드를 기다리던 중이면 바로 보여 준다
            _state.update { s ->
                when (s.run) {
                    is PlanRunState.Preparing -> s.copy(run = PlanRunState.Guide(option, steps))
                    is PlanRunState.Guide -> if (s.run.option.layout.arrangementId == option.layout.arrangementId) s.copy(run = s.run.copy(steps = steps)) else s
                    else -> s
                }
            }
        }
    }

    private suspend fun guideSteps(planId: String, calculationId: String, option: ArrangementOptionUi): List<GuideStepUi> {
        val guide = when (val result = remote.loadingGuideForPlan(planId, calculationId, option.layout.arrangementId)) {
            is ApiResult.Success -> result.data
            else -> {
                Log.w(TAG, "적재 안내 실패: $result")
                null
            }
        }
        return LoadingGuideBuilder.build(option.layout, guide)
    }

    // ── 계획 진행하기 ──
    fun proceed() {
        val s = _state.value
        if (s.planId == null || s.saving || s.run != null) return
        if (s.dirty) {
            _state.update { it.copy(confirmProceed = true) }
            return
        }
        if (needsRecalc || (server != null && server?.selectedCalculationId == null && server?.latestCalculationId == null)) {
            runChanged(save = false)
            return
        }
        val ready = prepared
        when {
            ready != null -> _state.update { it.copy(run = PlanRunState.Guide(ready.option, ready.steps)) }
            else -> {
                // 아직 받는 중(또는 실패) → 기다리는 화면, 받는 대로 가이드
                _state.update { it.copy(run = PlanRunState.Preparing("적재 가이드를 불러오고 있어요")) }
                if (server == null) {
                    // 서버 상세 → prefetch 순으로 받는다. 실패하면 loadServer가 기다림을 풀어 준다
                    local?.plan?.planId?.let(::loadServer)
                } else {
                    if (prepareJob?.isActive != true) prefetch()
                    awaitPrepared()
                }
            }
        }
    }

    /** 미리 받기가 끝났는데 배치안이 없으면 기다림을 풀고 안내 */
    private fun awaitPrepared() {
        viewModelScope.launch {
            prepareJob?.join()
            if (prepared == null && _state.value.run is PlanRunState.Preparing && !_state.value.saving) {
                _state.update { it.copy(run = null, message = "배치안을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.") }
            }
        }
    }

    fun dismissConfirm() = _state.update { it.copy(confirmProceed = false) }

    /** 확인 창 "저장하고 진행" */
    fun confirmProceed() {
        _state.update { it.copy(confirmProceed = false) }
        runChanged(save = true)
    }

    /** 바뀐 계획: 저장 → 재계산 → 배치안 선택 → 적재 안내 → 가이드 */
    private fun runChanged(save: Boolean) {
        val details = local ?: return
        _state.update { it.copy(run = PlanRunState.Preparing(if (save) "변경한 계획을 저장하고 있어요" else "배치를 다시 계산하고 있어요"), saving = true) }
        viewModelScope.launch {
            val planId = details.plan.planId
            if (save && !saveDraft(details)) return@launch
            needsRecalc = true
            prepareJob?.cancel()
            prepared = null

            _state.update { it.copy(run = PlanRunState.Preparing("바뀐 짐과 인원에 맞춰 배치를 다시 계산하고 있어요")) }
            val revision = server?.revision ?: return@launch stop("서버에서 계획 정보를 아직 받지 못했어요. 잠시 후 다시 시도해 주세요.")
            val calculation = when (val result = remote.recalculate(planId, CalculationCreateRequest(expectedRevision = revision, vehicleState = emptyVehicle()))) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> {
                    Log.w(TAG, "재계산 실패: $result")
                    if (result.httpCode == 409) loadServer(planId)
                    return@launch stop(result.message ?: "배치를 다시 계산하지 못했어요. 잠시 후 다시 시도해 주세요.")
                }
                is ApiResult.NetworkError -> return@launch stop("네트워크 연결을 확인하고 다시 시도해 주세요.")
            }
            val calculationId = calculation.calculationId
            val arrangement = calculation.arrangements.orEmpty().minByOrNull { it.rank }
            if (calculation.resultCode == "TIME_LIMIT") {
                // 저장은 됐고 계산만 시간 초과 → 다음 진행 때 다시 계산 (사용자가 누를 때)
                return@launch stop("제한 시간 안에 배치안을 찾지 못했어요. 잠시 후 다시 진행해 주세요.")
            }
            if (!calculation.isSuccess || arrangement == null || calculationId == null) {
                needsRecalc = false
                return@launch stop("바뀐 조건으로는 짐을 모두 실을 수 없어요. 짐이나 탑승 인원을 다시 확인해 주세요.")
            }
            needsRecalc = false
            val option = option(arrangement, calculation, draftPassengers ?: details.plan.passengerCount)
            _state.update { it.copy(saving = false, run = PlanRunState.Guide(option, steps = null)) }

            select(planId, revision, calculationId, arrangement.arrangementId)
            val key = "$calculationId/${arrangement.arrangementId}"
            prepared = Prepared(key, option, steps = null)
            val steps = guideSteps(planId, calculationId, option)
            prepared = Prepared(key, option, steps)
            _state.update { s ->
                val run = s.run
                if (run is PlanRunState.Guide && run.option.layout.arrangementId == option.layout.arrangementId) s.copy(run = run.copy(steps = steps)) else s
            }
        }
    }

    /** 이번 재계산의 1순위 배치안을 계획에 선택해 둔다 (다음 진행·미리보기·모바일에서 같은 배치) */
    private suspend fun select(planId: String, revision: Int, calculationId: String, arrangementId: String) {
        when (val result = remote.selectArrangement(planId, PlanSelectionRequest(revision, calculationId, arrangementId))) {
            is ApiResult.Success -> server = server?.copy(
                revision = result.data.revision,
                selectedCalculationId = result.data.selectedCalculationId,
                selectedArrangementId = result.data.selectedArrangementId,
            )
            else -> Log.w(TAG, "배치안 선택 저장 실패: $result")
        }
    }

    private fun stop(message: String) {
        _state.update { it.copy(saving = false, run = null, message = message) }
    }

    fun completeRun(by: LoadCompletion) = _state.update { s ->
        val run = s.run
        if (run is PlanRunState.Guide) {
            s.copy(run = PlanRunState.Complete(run.option, currentItems().map { it.toLoadItem(server) }, by))
        } else {
            s
        }
    }

    /** 가이드 "조건 변경" / 완료 "홈으로" → 상세 화면 */
    fun closeRun() = _state.update { it.copy(run = null) }

    /** 변경본을 서버에 한 번 저장 → 성공하면 기기 DB도 바꾸고 변경본을 비운다 */
    private suspend fun saveDraft(details: PlanWithDetails): Boolean {
        val base = server
        if (base?.revision == null) {
            if (base == null) loadServer(details.plan.planId)
            stop("서버에서 계획 정보를 아직 받지 못해 저장할 수 없어요. 잠시 후 다시 시도해 주세요.")
            return false
        }
        val passengerCount = draftPassengers ?: details.plan.passengerCount
        val items = currentItems()
        val serverItems = base.items.orEmpty().associateBy { it.itemId }
        val request = PlanUpdateRequest(
            expectedRevision = base.revision,
            plan = PlanInputRequest(
                name = details.plan.name.ifBlank { null },
                category = details.plan.category.name,
                memo = details.plan.memo,
                isFavorite = details.plan.isFavorite,
                vehicleId = base.vehicleId ?: details.plan.vehicleId,
                passengerCount = passengerCount,
                // 인원이 바뀌면 자리는 서버가 다시 정한다
                passengerSeats = if (passengerCount == base.passengerCount) base.passengerSeats else null,
                carSeats = base.carSeats.orEmpty(),
                retainedSeats = base.retainedSeats.orEmpty(),
                items = items.map { item ->
                    val original = serverItems[item.itemId]
                    PlanItemUpdateRequest(
                        itemId = item.itemId,
                        name = item.name,
                        widthCm = item.widthCm.toDouble(),
                        depthCm = item.depthCm.toDouble(),
                        heightCm = item.heightCm.toDouble(),
                        quantity = item.quantity,
                        requiredQuantity = (original?.requiredQuantity ?: 0).coerceAtMost(item.quantity),
                        allowedOrientations = original?.allowedOrientations,
                        transportState = original?.transportState ?: "UNKNOWN",
                        // 사용자가 숫자로 고쳤으면 직접 입력
                        dimensionSource = if (original != null && sameSize(original, item)) original.dimensionSource ?: "UNSPECIFIED" else "MANUAL",
                        material = original?.material,
                        savedItemId = original?.savedItemId,
                    )
                },
            ),
        )
        return when (val result = remote.updatePlan(details.plan.planId, request)) {
            is ApiResult.Success -> {
                server = result.data
                // 저장했으니 예전 선택 배치안은 더 이상 맞지 않음 → 재계산 전까지 미리보기도 서버에서
                needsRecalc = true
                prepared = null
                db.planDao().savePlan(details.plan.copy(passengerCount = passengerCount), items, details.carSeats)
                clearDraft()
                render()
                true
            }
            else -> {
                Log.w(TAG, "계획 수정 실패: $result")
                val conflict = (result as? ApiResult.Failure)?.httpCode == 409
                stop(if (conflict) "다른 기기에서 계획이 바뀌었어요. 최신 계획을 다시 불러올게요." else "계획을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.")
                if (conflict) {
                    clearDraft()
                    render()
                    loadServer(details.plan.planId)
                }
                false
            }
        }
    }

    private fun sameSize(original: com.windrr.hyundae.data.remote.model.PlanItemResponse, item: PlanItemEntity): Boolean =
        original.widthCm?.roundToInt() == item.widthCm && original.depthCm?.roundToInt() == item.depthCm && original.heightCm?.roundToInt() == item.heightCm

    private fun option(arrangement: com.windrr.hyundae.data.remote.model.ArrangementResponse, calculation: PreviewCalculation, passengerCount: Int): ArrangementOptionUi {
        val layout = ArrangementLayoutMapper.map(arrangement, calculation.inputSnapshot?.items.orEmpty())
        val count = arrangement.passengerSeats?.size ?: passengerCount
        val title = when {
            layout.foldedGroups.isEmpty() && arrangement.allInTrunk == true -> "트렁크에 모두 싣기"
            layout.foldedGroups.isEmpty() -> "빈 좌석까지 활용"
            else -> layout.foldedGroups.joinToString(" · ") { seatGroupName(it.seats) } + " 접기"
        }
        return ArrangementOptionUi(title = title, subtitle = "총 ${count}명 탑승 · $title", layout = layout, passengerCount = count)
    }

    private fun PlanItemEntity.toLoadItem(plan: PlanResponse?): LoadItem {
        val transport = plan?.items.orEmpty().firstOrNull { it.itemId == itemId }?.transportState
        return LoadItem(key = itemId, name = name, widthCm = widthCm, depthCm = depthCm, heightCm = heightCm, quantity = quantity, stateLabel = stateLabel(transport), transportState = transport)
    }

    private companion object {
        const val TAG = "PlanDetail"
        val ALL_SEATS = listOf("ROW_1_LEFT", "ROW_1_RIGHT", "ROW_2_LEFT", "ROW_2_CENTER", "ROW_2_RIGHT")

        /** 좌석·접은 좌석을 쓰려면 지금 비어 있는 좌석 → 전 좌석 비어 있음 (사용자 입력) */
        fun emptyVehicle() = VehicleStateInput(
            observedAt = OffsetDateTime.now().toString(),
            seats = ALL_SEATS.map { SeatObservationInput(seatCode = it, occupancy = "EMPTY", source = "USER") },
        )
        const val MAX_ITEM_QUANTITY = LoadCheckUiState.MAX_ITEMS

        fun stateLabel(code: String?): String? = when (code) {
            "PACKAGED" -> "포장 상태"
            "ASSEMBLED" -> "조립 상태"
            "FOLDED" -> "접은 상태"
            else -> null
        }
    }
}
