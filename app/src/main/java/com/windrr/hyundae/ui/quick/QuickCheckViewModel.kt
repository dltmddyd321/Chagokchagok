package com.windrr.hyundae.ui.quick

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.windrr.hyundae.data.local.AppDatabase
import com.windrr.hyundae.data.remote.ApiResult
import com.windrr.hyundae.data.remote.ProductRemoteRepository
import com.windrr.hyundae.ui.loadcheck.ProductCandidateUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 빠른 확인 화면 상태 */
sealed interface QuickCheckState {
    /** 듣는 중 (음성 orb) */
    data object Listening : QuickCheckState

    /** 말한 제품의 규격을 찾는 중 */
    data class Searching(val query: String) : QuickCheckState

    /** 판정 결과 */
    data class Result(
        val product: ProductCandidateUi,
        val fits: Boolean,
        /** 카드 아래 안내 한 줄 */
        val hint: String,
    ) : QuickCheckState

    /** 제품을 못 찾았거나 치수가 모자람·검색 불가 */
    data class Message(val title: String, val body: String) : QuickCheckState
}

/**
 * 빠른 확인: 제품명을 말하면 → 제품 규격 검색(POST /products/search, 한 번) → 기기 DB의 차량 실측으로 들어가는지 판정.
 * 배치 계산(서버)은 하지 않는다. "뒷좌석을 접으면 들어가?"처럼 물으면 방금 제품을 2열 접은 기준으로 다시 판정 (서버 요청 없음).
 */
class QuickCheckViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val productRemote = ProductRemoteRepository()

    private val _state = MutableStateFlow<QuickCheckState>(QuickCheckState.Listening)
    val state: StateFlow<QuickCheckState> = _state.asStateFlow()

    /** 마지막으로 판정한 제품 (접기 질문에 씀) */
    private var lastProduct: ProductCandidateUi? = null

    fun listenAgain() {
        _state.value = QuickCheckState.Listening
    }

    /** 음성 인식 결과 */
    fun onUtterance(text: String) {
        val said = text.trim()
        if (said.isEmpty()) return
        val product = lastProduct
        if (product != null && isFoldQuestion(said)) {
            viewModelScope.launch { judge(product, askedFold = true) }
            return
        }
        _state.value = QuickCheckState.Searching(said)
        viewModelScope.launch { search(said) }
    }

    private suspend fun search(query: String) {
        when (val result = productRemote.search(query)) {
            is ApiResult.Success -> {
                val candidates = ProductCandidateUi.sort(result.data.candidates.orEmpty().map(ProductCandidateUi::from))
                val product = candidates.firstOrNull { it.hasAllDimensions }
                when {
                    product != null -> {
                        lastProduct = product
                        judge(product, askedFold = false)
                    }
                    candidates.isNotEmpty() -> _state.value = QuickCheckState.Message(
                        title = "치수를 모두 확인하지 못했어요",
                        body = "“${candidates.first().name}”의 크기 정보가 부족해요. 다른 이름으로 말해 주세요.",
                    )
                    else -> _state.value = QuickCheckState.Message(
                        title = "제품을 찾지 못했어요",
                        body = "브랜드와 제품명을 함께 말해 주세요.",
                    )
                }
            }
            is ApiResult.Failure -> {
                Log.w(TAG, "제품 검색 실패: $result")
                _state.value = QuickCheckState.Message(
                    title = "제품을 찾지 못했어요",
                    body = result.message?.replace("AR 측정이나 직접 입력을 이용해 주세요.", "")?.trim()?.ifBlank { null } ?: "잠시 후 다시 말해 주세요.",
                )
            }
            is ApiResult.NetworkError -> _state.value = QuickCheckState.Message(
                title = "연결이 원활하지 않아요",
                body = "네트워크 연결을 확인하고 다시 말해 주세요.",
            )
        }
    }

    private suspend fun judge(product: ProductCandidateUi, askedFold: Boolean) {
        val spaces = db.vehicleCargoDao().mainVehicleSpaces()
        val fit = QuickFitChecker.check(product.widthCm ?: 0.0, product.depthCm ?: 0.0, product.heightCm ?: 0.0, spaces)
        _state.value = when (fit) {
            QuickFit.FITS -> QuickCheckState.Result(product, fits = true, hint = HINT_CHECK_STATE)
            QuickFit.FITS_WHEN_FOLDED -> if (askedFold) {
                QuickCheckState.Result(product, fits = true, hint = "뒷좌석을 접으면 실을 수 있어요. $HINT_CHECK_STATE")
            } else {
                QuickCheckState.Result(product, fits = false, hint = "“뒷좌석을 접으면 들어가?”라고 물어보세요")
            }
            QuickFit.NOT_FIT -> QuickCheckState.Result(
                product,
                fits = false,
                hint = if (askedFold) "뒷좌석을 접어도 들어가지 않아요" else "뒷좌석을 접어도 들어가지 않는 크기예요",
            )
        }
    }

    private companion object {
        const val TAG = "QuickCheck"
        const val HINT_CHECK_STATE = "싣기 전에 실제로 옮길 상태의 크기를 확인하세요."

        /** "뒷좌석 접으면 들어가?", "시트 접으면 돼?" 등 */
        fun isFoldQuestion(text: String): Boolean {
            val compact = text.replace(" ", "")
            return compact.contains("접") && (compact.contains("좌석") || compact.contains("시트") || compact.contains("뒷") || compact.contains("2열"))
        }
    }
}
