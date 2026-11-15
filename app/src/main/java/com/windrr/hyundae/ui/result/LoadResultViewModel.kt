package com.windrr.hyundae.ui.result

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrr.hyundae.data.remote.ApiResult
import com.windrr.hyundae.data.remote.PlanRemoteRepository
import com.windrr.hyundae.data.remote.model.ArrangementResponse
import com.windrr.hyundae.data.remote.model.CalculationOptions
import com.windrr.hyundae.data.remote.model.PreviewCalculation
import com.windrr.hyundae.data.remote.model.PreviewItemInput
import com.windrr.hyundae.data.remote.model.PreviewPlanInput
import com.windrr.hyundae.data.remote.model.PreviewRequest
import com.windrr.hyundae.data.remote.model.SeatObservationInput
import com.windrr.hyundae.data.remote.model.VehicleStateInput
import com.windrr.hyundae.ui.loadcheck.LoadItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.util.UUID

/** 적재 가능 여부 확인에서 넘어온 값 */
data class LoadResultInput(
    val items: List<LoadItem>,
    /** 운전자 포함 */
    val passengerCount: Int,
)

/** 추천 방법 한 줄 (= 서버 배치안 하나) */
data class ArrangementOptionUi(
    val title: String,
    val subtitle: String,
    val layout: ArrangementLayoutUi,
    /** 이 방법에서 싣지 못하는 짐 이름 (짐 빼고 싣기 대안) */
    val excludedNames: List<String> = emptyList(),
    /** 이 배치의 탑승 인원 (운전자 포함) */
    val passengerCount: Int = 0,
)

/** 적재 완료 계기 */
enum class LoadCompletion { BUTTON, TRUNK_CLOSED }

sealed interface LoadResultState {
    data object Loading : LoadResultState

    /**
     * 계산 결과.
     * [possible] = 지금 조건 그대로 실을 수 있음. 불가능이면 [options]는 서버가 같이 준 짐 빼고 싣기 대안.
     * 대안도 없으면 비어 있다.
     */
    data class Result(
        val possible: Boolean,
        val options: List<ArrangementOptionUi>,
        /** 처음엔 미선택 (사용자가 추천 방법을 고르면 탑뷰에 그린다) */
        val selectedIndex: Int? = null,
        /** 배치안별 적재 안내 (arrangement_id → 단계, null = 받는 중). 적재 가능이면 결과가 나오자마자 받기 시작 */
        val guides: Map<String, List<GuideStepUi>?> = emptyMap(),
        /** 이 조건으로 진행 → 적재 가이드 화면 */
        val proceeding: Boolean = false,
        /** 적재 완료 (가이드에서 "짐을 다 실었어요" 또는 트렁크 닫힘 감지) */
        val completion: LoadCompletion? = null,
    ) : LoadResultState {
        val selectedGuide: List<GuideStepUi>? get() = selected?.let { guides[it.layout.arrangementId] }

        val selected: ArrangementOptionUi? get() = selectedIndex?.let { options.getOrNull(it) }
    }

    data class Error(val message: String) : LoadResultState
}

/**
 * 적재 결과: 화면에 들어오면 POST /calculations/preview.
 * - 좌석 위치는 보내지 않고 인원 수만 → 서버가 사람 자리를 정해 배치안에 넣는다.
 * - 좌석·접은 좌석을 쓰려면 "지금 비어 있는 좌석"이 필요해서 전 좌석을 비어 있음(사용자 입력)으로 보낸다.
 * - 서버 요청은 한 번만. 짐 제외를 허용(allow_exclusions)해 두면, 다 못 실을 때
 *   서버가 "이 짐을 빼면 나머지는 실림" 대안 배치(뺄 짐 이름·이유)를 같이 준다 → 불가능 화면의 추천 방법.
 * 새 계산마다 새 Idempotency-Key, 통신 오류 후 다시 시도는 같은 키.
 */
class LoadResultViewModel(
    private val remote: PlanRemoteRepository = PlanRemoteRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<LoadResultState>(LoadResultState.Loading)
    val state: StateFlow<LoadResultState> = _state.asStateFlow()

    private var input: LoadResultInput? = null

    /** 이 계산 요청의 키. 통신 오류로 다시 시도할 때만 같은 값 */
    private var idempotencyKey = UUID.randomUUID().toString()

    fun start(input: LoadResultInput) {
        if (this.input != null) return
        this.input = input
        load()
    }

    fun retry() = load()

    fun select(index: Int) = _state.update { s ->
        if (s is LoadResultState.Result && index in s.options.indices) s.copy(selectedIndex = index) else s
    }

    /** 이 조건으로 진행 → 적재 가이드 */
    fun proceed() = _state.update { s ->
        if (s is LoadResultState.Result && s.selected != null) s.copy(proceeding = true) else s
    }

    /** 가이드 → 적재 완료 */
    fun complete(by: LoadCompletion) = _state.update { s ->
        if (s is LoadResultState.Result && s.proceeding && s.completion == null) s.copy(completion = by) else s
    }

    /** 가이드의 조건 변경 → 결과 화면 */
    fun backToResult() = _state.update { s -> if (s is LoadResultState.Result) s.copy(proceeding = false) else s }

    /** 가이드 화면을 보는 중이면 뒤로가기는 결과 화면으로 */
    val isProceeding: Boolean get() = (_state.value as? LoadResultState.Result)?.proceeding == true

    private fun load() {
        val current = input ?: return
        _state.value = LoadResultState.Loading
        viewModelScope.launch {
            var previewId = ""
            val first = when (val result = remote.previewCalculation(buildRequest(current), idempotencyKey)) {
                is ApiResult.Success -> result.data.calculation.also { previewId = result.data.previewId }
                is ApiResult.Failure -> {
                    // 서버가 요청을 거절(4xx)했으면 같은 키로 다시 보내도 결과가 같다 → 새 키
                    if (result.httpCode in 400..499) idempotencyKey = UUID.randomUUID().toString()
                    return@launch fail(result.message ?: "배치를 계산하지 못했어요.", result)
                }
                is ApiResult.NetworkError -> return@launch fail("네트워크 연결을 확인하고 다시 시도해 주세요.", result)
            }
            if (first.isSuccess && first.resultCode == RESULT_LAYOUT_FOUND && !first.arrangements.isNullOrEmpty()) {
                val options = first.arrangements.orEmpty().sortedBy { it.rank }.take(MAX_OPTIONS).map { option(it, first, current.passengerCount) }
                _state.value = LoadResultState.Result(
                    possible = true,
                    options = options,
                    guides = options.associate { it.layout.arrangementId to null },
                )
                // 적재 가능이면 사용자가 진행을 누르기 전에 배치안마다 적재 안내를 미리 받아 둔다
                options.forEach { loadGuide(previewId, it.layout) }
                return@launch
            }
            Log.i(TAG, "적재 불가 ${first.resultCode}, 짐 빼기 대안 ${first.alternatives?.size ?: 0}개")
            // 불가능일 때 추천 방법 = 서버가 같이 준 짐 빼기 대안
            val options = first.alternatives.orEmpty().sortedBy { it.rank }.take(MAX_OPTIONS).map { option(it, first, current.passengerCount) }
            _state.value = LoadResultState.Result(possible = false, options = options)
        }
    }

    /** 적재 안내 하나. 서버가 단계를 비워 주거나 실패하면 시안 기본 문구로 */
    private fun loadGuide(previewId: String, layout: ArrangementLayoutUi) {
        viewModelScope.launch {
            val guide = when (val result = remote.loadingGuide(previewId, layout.arrangementId)) {
                is ApiResult.Success -> result.data
                else -> {
                    Log.w(TAG, "적재 안내 실패 ${layout.arrangementId}: $result")
                    null
                }
            }
            val steps = LoadingGuideBuilder.build(layout, guide)
            _state.update { s ->
                if (s is LoadResultState.Result && layout.arrangementId in s.guides) s.copy(guides = s.guides + (layout.arrangementId to steps)) else s
            }
        }
    }

    private fun option(arrangement: ArrangementResponse, calculation: PreviewCalculation, passengerCount: Int): ArrangementOptionUi {
        val layout = ArrangementLayoutMapper.map(arrangement, calculation.inputSnapshot?.items.orEmpty())
        val excluded = arrangement.exclusions.orEmpty().map { it.name }
        val count = arrangement.passengerSeats?.size ?: passengerCount
        val people = "총 ${count}명 탑승"
        return if (excluded.isEmpty()) {
            ArrangementOptionUi(title = optionTitle(layout, arrangement.allInTrunk == true), subtitle = people, layout = layout, passengerCount = count)
        } else {
            ArrangementOptionUi(
                title = "${excluded.joinToString(", ")} 빼고 싣기",
                subtitle = "$people · ${optionTitle(layout, arrangement.allInTrunk == true)}",
                layout = layout,
                excludedNames = excluded,
                passengerCount = count,
            )
        }
    }

    private fun fail(message: String, result: ApiResult<*>) {
        Log.w(TAG, "배치 계산 실패: $result")
        _state.value = LoadResultState.Error(message)
    }

    /** "트렁크에 모두 싣기" / "2열 왼쪽 좌석 접기" 등 */
    private fun optionTitle(layout: ArrangementLayoutUi, allInTrunk: Boolean): String = when {
        layout.foldedGroups.isEmpty() && allInTrunk -> "트렁크에 모두 싣기"
        layout.foldedGroups.isEmpty() -> "빈 좌석까지 활용"
        else -> layout.foldedGroups.joinToString(" · ") { seatGroupName(it.seats) } + " 접기"
    }

    private companion object {
        const val TAG = "LoadResult"
        const val RESULT_LAYOUT_FOUND = "LAYOUT_FOUND"
        const val MAX_OPTIONS = 3

        /** 서버 차량 (1차는 고정) */
        const val SERVER_VEHICLE_ID = "avante_demo"

        val ALL_SEATS = listOf("ROW_1_LEFT", "ROW_1_RIGHT", "ROW_2_LEFT", "ROW_2_CENTER", "ROW_2_RIGHT")

        fun buildRequest(input: LoadResultInput) = PreviewRequest(
            plan = PreviewPlanInput(
                vehicleId = SERVER_VEHICLE_ID,
                passengerCount = input.passengerCount,
                // 자리는 서버가 정한다
                passengerSeats = null,
                items = input.items.map { item ->
                    PreviewItemInput(
                        name = item.name.ifBlank { "짐" },
                        widthCm = item.widthCm.toDouble(),
                        depthCm = item.depthCm.toDouble(),
                        heightCm = item.heightCm.toDouble(),
                        quantity = item.quantity,
                        transportState = item.transportState ?: "UNKNOWN",
                        dimensionSource = if (item.transportState != null) "PRODUCT_SOURCE" else "MANUAL",
                    )
                },
            ),
            calculationOptions = CalculationOptions(
                // 다 못 실으면 "이 짐을 빼면 실림" 대안을 같이 받는다
                allowExclusions = true,
                vehicleState = VehicleStateInput(
                    observedAt = OffsetDateTime.now().toString(),
                    seats = ALL_SEATS.map { SeatObservationInput(seatCode = it, occupancy = "EMPTY", source = "USER") },
                ),
            ),
        )
    }
}
