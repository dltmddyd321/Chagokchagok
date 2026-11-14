package com.windrr.hyundae.ui.loadcheck

import androidx.lifecycle.ViewModel
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.windrr.hyundae.data.remote.ApiResult
import com.windrr.hyundae.data.remote.ProductRemoteRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class LoadCheckViewModel(
    private val productRemote: ProductRemoteRepository = ProductRemoteRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(LoadCheckUiState())
    val state: StateFlow<LoadCheckUiState> = _state.asStateFlow()

    private val _effect = Channel<LoadCheckEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    fun onIntent(intent: LoadCheckIntent) {
        when (intent) {
            LoadCheckIntent.IncreaseCompanions -> _state.update {
                it.copy(companions = (it.companions + 1).coerceAtMost(LoadCheckUiState.MAX_COMPANIONS))
            }
            LoadCheckIntent.DecreaseCompanions -> _state.update { it.copy(companions = (it.companions - 1).coerceAtLeast(0)) }
            is LoadCheckIntent.RemoveItem -> _state.update { s -> s.copy(items = s.items.filterNot { it.key == intent.key }) }
            LoadCheckIntent.OpenManualInput -> _state.update { if (it.canAddItem) it.copy(manualInput = ManualInputState()) else it }
            is LoadCheckIntent.OpenManualInputWithName -> _state.update {
                if (it.canAddItem) it.copy(manualInput = ManualInputState(name = intent.name.take(MAX_NAME_LENGTH))) else it
            }
            LoadCheckIntent.CloseManualInput -> _state.update { it.copy(manualInput = null) }
            is LoadCheckIntent.ManualNameChanged -> updateManual {
                it.copy(name = intent.name.replace("\n", "").take(MAX_NAME_LENGTH))
            }
            is LoadCheckIntent.StepManualQuantity -> _state.update { s ->
                val input = s.manualInput ?: return@update s
                // 이미 담은 짐과 합쳐 최대 개수를 넘지 않게
                val max = (LoadCheckUiState.MAX_ITEMS - s.itemCount).coerceAtLeast(1)
                s.copy(manualInput = input.copy(quantity = (input.quantity + intent.delta).coerceIn(1, max)))
            }
            is LoadCheckIntent.SelectField -> updateManual { it.copy(selected = intent.field) }
            is LoadCheckIntent.StepField -> updateManual {
                val current = it.valueOf(intent.field).toIntOrNull() ?: 0
                val next = (current + intent.delta).coerceIn(0, LoadCheckUiState.MAX_CM)
                it.with(intent.field, if (next == 0) "" else next.toString()).copy(selected = intent.field)
            }
            is LoadCheckIntent.KeypadDigit -> updateManual {
                val current = it.valueOf(it.selected)
                val next = (current + intent.digit).trimStart('0')
                // 최대 3자리, 300cm 이하
                if (next.length > 3 || (next.toIntOrNull() ?: 0) > LoadCheckUiState.MAX_CM) it else it.with(it.selected, next)
            }
            LoadCheckIntent.KeypadClear -> updateManual { it.with(it.selected, "") }
            LoadCheckIntent.KeypadBackspace -> updateManual { it.with(it.selected, it.valueOf(it.selected).dropLast(1)) }
            is LoadCheckIntent.EditItem -> _state.update { s ->
                val item = s.items.firstOrNull { it.key == intent.key } ?: return@update s
                s.copy(
                    manualInput = ManualInputState(
                        name = item.name,
                        quantity = item.quantity,
                        width = item.widthCm.toString(),
                        depth = item.depthCm.toString(),
                        height = item.heightCm.toString(),
                        editingKey = item.key,
                        stateLabel = item.stateLabel,
                    ),
                )
            }
            LoadCheckIntent.ConfirmManualInput -> _state.update { s ->
                val input = s.manualInput ?: return@update s
                // 치수 수정: 그 짐의 가로·세로·높이만 바꾼다 (이름·수량·운반 상태 유지)
                input.editingKey?.let { key ->
                    val edited = input.toItem(key, "") ?: return@update s
                    return@update s.copy(
                        items = s.items.map {
                            if (it.key == key) it.copy(widthCm = edited.widthCm, depthCm = edited.depthCm, heightCm = edited.heightCm) else it
                        },
                        manualInput = null,
                    )
                }
                val item = s.manualInput?.toItem("manual-${UUID.randomUUID()}", "짐 ${s.items.size + 1}") ?: return@update s
                if (s.itemCount + item.quantity > LoadCheckUiState.MAX_ITEMS) return@update s
                s.copy(items = s.items + item, manualInput = null)
            }
            is LoadCheckIntent.SearchProduct -> searchProduct(intent.query)
            is LoadCheckIntent.SelectProductCandidate -> _state.update { s ->
                val found = s.productSearch as? ProductSearchState.Found ?: return@update s
                if (intent.index !in found.candidates.indices) s else s.copy(productSearch = found.copy(selectedIndex = intent.index))
            }
            LoadCheckIntent.AddProductCandidate -> _state.update { s ->
                val candidate = (s.productSearch as? ProductSearchState.Found)?.selected ?: return@update s
                val w = candidate.intCm(candidate.widthCm)
                val d = candidate.intCm(candidate.depthCm)
                val h = candidate.intCm(candidate.heightCm)
                if (w == null || d == null || h == null || !s.canAddItem) return@update s
                val item = LoadItem(
                    key = "product-${candidate.candidateId}",
                    name = candidate.name.take(MAX_NAME_LENGTH),
                    widthCm = w,
                    depthCm = d,
                    heightCm = h,
                    stateLabel = candidate.transportState.shortLabel,
                )
                s.copy(items = s.items + item, productSearch = null)
            }
            LoadCheckIntent.EnterProductDimensions -> _state.update { s ->
                val search = s.productSearch ?: return@update s
                if (!s.canAddItem) return@update s.copy(productSearch = null)
                val candidate = (search as? ProductSearchState.Found)?.selected
                val input = ManualInputState(
                    name = (candidate?.name ?: search.query).take(MAX_NAME_LENGTH),
                    width = candidate?.intCm(candidate.widthCm)?.toString().orEmpty(),
                    depth = candidate?.intCm(candidate.depthCm)?.toString().orEmpty(),
                    height = candidate?.intCm(candidate.heightCm)?.toString().orEmpty(),
                )
                // 비어 있는 첫 치수부터 입력하게
                val firstEmpty = ManualField.entries.firstOrNull { input.valueOf(it).isEmpty() } ?: ManualField.WIDTH
                s.copy(productSearch = null, manualInput = input.copy(selected = firstEmpty))
            }
            LoadCheckIntent.CloseProductSearch -> _state.update { it.copy(productSearch = null) }
            LoadCheckIntent.Check -> {
                val s = _state.value
                if (!s.canCheck) return
                viewModelScope.launch { _effect.send(LoadCheckEffect.RequestCheck(s.items, s.totalPassengers)) }
            }
        }
    }

    /** 새 검색마다 새 Idempotency-Key (저장소 기본값) */
    private fun searchProduct(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        _state.update { it.copy(productSearch = ProductSearchState.Searching(text)) }
        viewModelScope.launch {
            val next = when (val result = productRemote.search(text)) {
                is ApiResult.Success -> {
                    val candidates = ProductCandidateUi.sort(result.data.candidates.orEmpty().map(ProductCandidateUi::from))
                    if (candidates.isEmpty()) {
                        ProductSearchState.NotFound(text, "제품 규격을 찾지 못했어요. 치수를 직접 입력해 주세요.")
                    } else {
                        ProductSearchState.Found(text, candidates)
                    }
                }
                is ApiResult.Failure -> {
                    Log.w(TAG, "제품 검색 실패: $result")
                    // 503 PRODUCT_SEARCH_UNAVAILABLE 등은 서버 문구를 그대로 (차에서는 AR 대신 직접 입력)
                    ProductSearchState.NotFound(text, result.message?.replace("AR 측정이나 ", "") ?: "제품 규격을 찾지 못했어요.")
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "제품 검색 통신 오류", result.cause)
                    ProductSearchState.NotFound(text, "네트워크 연결을 확인해 주세요. 치수를 직접 입력할 수도 있어요.")
                }
            }
            // 그 사이 창을 닫았으면 결과를 띄우지 않는다
            _state.update { if (it.productSearch == ProductSearchState.Searching(text)) it.copy(productSearch = next) else it }
        }
    }

    private fun updateManual(transform: (ManualInputState) -> ManualInputState) {
        _state.update { s -> s.manualInput?.let { s.copy(manualInput = transform(it)) } ?: s }
    }

    private companion object {
        const val TAG = "LoadCheck"
        const val MAX_NAME_LENGTH = 20
    }
}
