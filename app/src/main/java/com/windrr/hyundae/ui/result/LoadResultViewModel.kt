package com.windrr.hyundae.ui.result

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrr.hyundae.data.remote.ApiResult
import com.windrr.hyundae.data.remote.PlanRemoteRepository
import com.windrr.hyundae.data.remote.model.CalculationOptions
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
)

sealed interface LoadResultState {
    data object Loading : LoadResultState

    data class Possible(val previewId: String, val options: List<ArrangementOptionUi>, val selectedIndex: Int = 0) : LoadResultState {
        val selected: ArrangementOptionUi? get() = options.getOrNull(selectedIndex)
    }

    data class Impossible(val resultCode: String) : LoadResultState

    data class Error(val message: String) : LoadResultState
}

/**
 * 적재 결과: 화면에 들어오면 한 번 POST /calculations/preview.
 * 좌석 위치는 보내지 않고 인원 수만 보낸다 → 서버가 사람 자리를 정해 배치안에 넣어 준다.
 * 좌석·접은 좌석까지 쓰려면 "지금 비어 있는 좌석"이 필요해서 전 좌석을 비어 있음(사용자 입력)으로 보낸다.
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
        if (s is LoadResultState.Possible && index in s.options.indices) s.copy(selectedIndex = index) else s
    }

    private fun load() {
        val body = input?.let(::buildRequest) ?: return
        _state.value = LoadResultState.Loading
        viewModelScope.launch {
            _state.value = when (val result = remote.previewCalculation(body, idempotencyKey)) {
                is ApiResult.Success -> {
                    val calculation = result.data.calculation
                    val items = calculation.inputSnapshot?.items.orEmpty()
                    val options = calculation.arrangements.orEmpty()
                        .sortedBy { it.rank }
                        .take(MAX_OPTIONS)
                        .map { arrangement ->
                            val layout = ArrangementLayoutMapper.map(arrangement, items)
                            ArrangementOptionUi(
                                title = optionTitle(layout, arrangement.allInTrunk == true),
                                subtitle = "총 ${arrangement.passengerSeats?.size ?: body.plan.passengerCount}명 탑승",
                                layout = layout,
                            )
                        }
                    if (calculation.resultCode == "LAYOUT_FOUND" && options.isNotEmpty()) {
                        LoadResultState.Possible(result.data.previewId, options)
                    } else {
                        LoadResultState.Impossible(calculation.resultCode)
                    }
                }
                is ApiResult.Failure -> {
                    Log.w(TAG, "배치 계산 실패: $result")
                    if (result.httpCode in 400..499) idempotencyKey = UUID.randomUUID().toString()
                    LoadResultState.Error(result.message ?: "배치를 계산하지 못했어요.")
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "배치 계산 통신 오류", result.cause)
                    LoadResultState.Error("네트워크 연결을 확인하고 다시 시도해 주세요.")
                }
            }
        }
    }

    /** "트렁크에 모두 싣기" / "2열 왼쪽 좌석 접기" 등 */
    private fun optionTitle(layout: ArrangementLayoutUi, allInTrunk: Boolean): String = when {
        layout.foldedGroups.isEmpty() && allInTrunk -> "트렁크에 모두 싣기"
        layout.foldedGroups.isEmpty() -> "빈 좌석까지 활용"
        else -> layout.foldedGroups.joinToString(" · ") { seatGroupName(it.seats) } + " 접기"
    }

    private companion object {
        const val TAG = "LoadResult"
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
                vehicleState = VehicleStateInput(
                    observedAt = OffsetDateTime.now().toString(),
                    seats = ALL_SEATS.map { SeatObservationInput(seatCode = it, occupancy = "EMPTY", source = "USER") },
                ),
            ),
        )
    }
}
