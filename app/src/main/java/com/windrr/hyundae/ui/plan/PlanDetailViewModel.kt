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
import com.windrr.hyundae.data.remote.model.PlanInputRequest
import com.windrr.hyundae.data.remote.model.PlanItemUpdateRequest
import com.windrr.hyundae.data.remote.model.PlanResponse
import com.windrr.hyundae.data.remote.model.PlanUpdateRequest
import com.windrr.hyundae.ui.loadcheck.LoadCheckIntent
import com.windrr.hyundae.ui.loadcheck.LoadCheckUiState
import com.windrr.hyundae.ui.loadcheck.ManualInputState
import com.windrr.hyundae.ui.result.ArrangementLayoutMapper
import com.windrr.hyundae.ui.result.ArrangementLayoutUi
import com.windrr.hyundae.ui.result.foldDescription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
 * - 짐 수정·동승자 변경은 사용자 동작 한 번에 PUT /plans/{id} 한 번 (expected_revision). 성공하면 기기 DB도 바꾼다.
 *   실패하면 안내만 하고, 다음 동기화 때 서버 값으로 돌아간다.
 */
class PlanDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val remote = PlanRemoteRepository()

    private val _state = MutableStateFlow(PlanDetailUiState())
    val state: StateFlow<PlanDetailUiState> = _state.asStateFlow()

    private var local: PlanWithDetails? = null
    private var server: PlanResponse? = null

    init {
        viewModelScope.launch {
            db.planDao().observePlans().collect { plans ->
                val latest = plans.firstOrNull()
                val changedPlan = latest?.plan?.planId != local?.plan?.planId
                local = latest
                if (changedPlan) server = null
                render()
                if (changedPlan && latest != null) loadServer(latest.plan.planId)
            }
        }
    }

    private fun loadServer(planId: String) {
        viewModelScope.launch {
            when (val result = remote.plan(planId)) {
                is ApiResult.Success -> if (local?.plan?.planId == planId) {
                    server = result.data
                    render()
                }
                else -> Log.w(TAG, "계획 상세 조회 실패: $result")
            }
        }
    }

    private fun render() {
        val details = local
        val serverItems = server?.items.orEmpty().associateBy { it.itemId }
        _state.update { s ->
            if (details == null) {
                PlanDetailUiState(loading = false)
            } else {
                s.copy(
                    loading = false,
                    planId = details.plan.planId,
                    name = details.plan.name.ifBlank { "이름 없는 계획" },
                    companions = (details.plan.passengerCount - 1).coerceIn(0, PlanDetailUiState.MAX_COMPANIONS),
                    items = details.items.map { item ->
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
                )
            }
        }
    }

    // ── 동승자 ──
    fun changeCompanions(delta: Int) {
        val s = _state.value
        val next = (s.companions + delta).coerceIn(0, PlanDetailUiState.MAX_COMPANIONS)
        if (next == s.companions || s.saving) return
        save(passengerCount = next + 1, items = currentItems())
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
        _state.update { it.copy(editing = null) }
        save(passengerCount = _state.value.totalPassengers, items = items)
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ── 배치도 미리보기: 저장할 때 고른 배치안(selected_calculation_id / selected_arrangement_id)을 받아 그린다 ──
    fun openPreview() {
        val planId = _state.value.planId ?: return
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

    private fun currentItems(): List<PlanItemEntity> = local?.items.orEmpty()

    /** 서버에 한 번 저장 → 성공하면 기기 DB에도 반영 */
    private fun save(passengerCount: Int, items: List<PlanItemEntity>) {
        val details = local ?: return
        val base = server
        if (base?.revision == null) {
            _state.update { it.copy(message = "서버에서 계획 정보를 아직 받지 못해 저장할 수 없어요. 잠시 후 다시 시도해 주세요.") }
            if (base == null) loadServer(details.plan.planId)
            return
        }
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
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = remote.updatePlan(details.plan.planId, request)) {
                is ApiResult.Success -> {
                    server = result.data
                    db.planDao().savePlan(details.plan.copy(passengerCount = passengerCount), items, details.carSeats)
                    _state.update { it.copy(saving = false) }
                }
                else -> {
                    Log.w(TAG, "계획 수정 실패: $result")
                    val conflict = (result as? ApiResult.Failure)?.httpCode == 409
                    _state.update {
                        it.copy(
                            saving = false,
                            message = if (conflict) "다른 기기에서 계획이 바뀌었어요. 최신 계획을 다시 불러올게요." else "계획을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.",
                        )
                    }
                    if (conflict) loadServer(details.plan.planId)
                }
            }
        }
    }

    private fun sameSize(original: com.windrr.hyundae.data.remote.model.PlanItemResponse, item: PlanItemEntity): Boolean =
        original.widthCm?.roundToInt() == item.widthCm && original.depthCm?.roundToInt() == item.depthCm && original.heightCm?.roundToInt() == item.heightCm

    private companion object {
        const val TAG = "PlanDetail"
        const val MAX_ITEM_QUANTITY = LoadCheckUiState.MAX_ITEMS

        fun stateLabel(code: String?): String? = when (code) {
            "PACKAGED" -> "포장 상태"
            "ASSEMBLED" -> "조립 상태"
            "FOLDED" -> "접은 상태"
            else -> null
        }
    }
}
