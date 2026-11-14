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
)

sealed interface LoadResultState {
    data object Loading : LoadResultState

    /**
     * 계산 결과.
     * [possible] = 지금 조건 그대로 실을 수 있음. 불가능이면 [options]는 앱이 조건을 바꿔 다시 계산해 찾은 대안
     * (짐 빼고 싣기 · 동승자 줄이기). 대안도 없으면 비어 있다.
     */
    data class Result(
        val possible: Boolean,
        val options: List<ArrangementOptionUi>,
        val selectedIndex: Int = 0,
    ) : LoadResultState {
        val selected: ArrangementOptionUi? get() = options.getOrNull(selectedIndex)
    }

    data class Error(val message: String) : LoadResultState
}

/**
 * 적재 결과: 화면에 들어오면 POST /calculations/preview.
 * - 좌석 위치는 보내지 않고 인원 수만 → 서버가 사람 자리를 정해 배치안에 넣는다.
 * - 좌석·접은 좌석을 쓰려면 "지금 비어 있는 좌석"이 필요해서 전 좌석을 비어 있음(사용자 입력)으로 보낸다.
 * - 다 못 실으면 서버가 원인을 주지 않아서, 대안을 직접 찾는다:
 *   ① 짐 제외 허용(allow_exclusions) → 서버가 "이 짐을 빼면 나머지는 실림" 배치를 준다 (뺄 짐 이름·이유)
 *   ② 동승자를 한 명씩 줄여 다시 계산 → 처음 실리는 인원을 추천
 * 요청마다 새 Idempotency-Key (조건이 다른 새 계산).
 */
class LoadResultViewModel(
    private val remote: PlanRemoteRepository = PlanRemoteRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<LoadResultState>(LoadResultState.Loading)
    val state: StateFlow<LoadResultState> = _state.asStateFlow()

    private var input: LoadResultInput? = null

    fun start(input: LoadResultInput) {
        if (this.input != null) return
        this.input = input
        load()
    }

    fun retry() = load()

    fun select(index: Int) = _state.update { s ->
        if (s is LoadResultState.Result && index in s.options.indices) s.copy(selectedIndex = index) else s
    }

    private fun load() {
        val current = input ?: return
        _state.value = LoadResultState.Loading
        viewModelScope.launch {
            val first = when (val result = preview(current, current.passengerCount)) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> return@launch fail(result.message ?: "배치를 계산하지 못했어요.", result)
                is ApiResult.NetworkError -> return@launch fail("네트워크 연결을 확인하고 다시 시도해 주세요.", result)
            }
            if (first.isSuccess && first.resultCode == RESULT_LAYOUT_FOUND && !first.arrangements.isNullOrEmpty()) {
                val options = first.arrangements.orEmpty().sortedBy { it.rank }.take(MAX_OPTIONS).map { option(it, first, current.passengerCount) }
                _state.value = LoadResultState.Result(possible = true, options = options)
                return@launch
            }
            Log.i(TAG, "적재 불가 ${first.resultCode} → 대안 계산")
            _state.value = LoadResultState.Result(possible = false, options = alternatives(current, first))
        }
    }

    /** 실패했을 때 대안: ① 서버가 준 짐 제외 배치 ② 동승자 줄이기 */
    private suspend fun alternatives(current: LoadResultInput, first: PreviewCalculation): List<ArrangementOptionUi> {
        val options = mutableListOf<ArrangementOptionUi>()
        first.alternatives.orEmpty().sortedBy { it.rank }.take(MAX_EXCLUSION_OPTIONS).forEach { arrangement ->
            options += option(arrangement, first, current.passengerCount)
        }
        // 동승자를 한 명씩 줄여 처음 다 실리는 인원 (운전자 1명까지)
        for (count in current.passengerCount - 1 downTo 1) {
            val calculation = (preview(current, count, allowExclusions = false) as? ApiResult.Success)?.data ?: break
            val best = calculation.arrangements.orEmpty().minByOrNull { it.rank }
            if (calculation.isSuccess && best != null) {
                val base = option(best, calculation, count)
                options += base.copy(title = "동승자 ${count - 1}명으로 조정", subtitle = "총 ${count}명 탑승 · ${base.title}")
                break
            }
        }
        return options.take(MAX_OPTIONS)
    }

    private fun option(arrangement: ArrangementResponse, calculation: PreviewCalculation, passengerCount: Int): ArrangementOptionUi {
        val layout = ArrangementLayoutMapper.map(arrangement, calculation.inputSnapshot?.items.orEmpty())
        val excluded = arrangement.exclusions.orEmpty().map { it.name }
        val people = "총 ${arrangement.passengerSeats?.size ?: passengerCount}명 탑승"
        return if (excluded.isEmpty()) {
            ArrangementOptionUi(title = optionTitle(layout, arrangement.allInTrunk == true), subtitle = people, layout = layout)
        } else {
            ArrangementOptionUi(
                title = "${excluded.joinToString(", ")} 빼고 싣기",
                subtitle = "$people · ${optionTitle(layout, arrangement.allInTrunk == true)}",
                layout = layout,
                excludedNames = excluded,
            )
        }
    }

    private suspend fun preview(input: LoadResultInput, passengerCount: Int, allowExclusions: Boolean = true): ApiResult<PreviewCalculation> =
        when (val result = remote.previewCalculation(buildRequest(input, passengerCount, allowExclusions), UUID.randomUUID().toString())) {
            is ApiResult.Success -> ApiResult.Success(result.data.calculation)
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
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
        const val MAX_EXCLUSION_OPTIONS = 2

        /** 서버 차량 (1차는 고정) */
        const val SERVER_VEHICLE_ID = "avante_demo"

        val ALL_SEATS = listOf("ROW_1_LEFT", "ROW_1_RIGHT", "ROW_2_LEFT", "ROW_2_CENTER", "ROW_2_RIGHT")

        fun buildRequest(input: LoadResultInput, passengerCount: Int, allowExclusions: Boolean) = PreviewRequest(
            plan = PreviewPlanInput(
                vehicleId = SERVER_VEHICLE_ID,
                passengerCount = passengerCount,
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
                allowExclusions = allowExclusions,
                vehicleState = VehicleStateInput(
                    observedAt = OffsetDateTime.now().toString(),
                    seats = ALL_SEATS.map { SeatObservationInput(seatCode = it, occupancy = "EMPTY", source = "USER") },
                ),
            ),
        )
    }
}
