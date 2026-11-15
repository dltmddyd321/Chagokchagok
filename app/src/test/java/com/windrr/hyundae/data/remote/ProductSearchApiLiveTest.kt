package com.windrr.hyundae.data.remote

import com.windrr.hyundae.BuildConfig
import com.windrr.hyundae.ui.loadcheck.ProductCandidateUi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 실제 서버 호출 확인: POST /products/search → 앱 모델(ProductCandidateUi) 변환까지.
 * 검색어는 환경변수 CHAGOK_TEST_PRODUCT_QUERY (기본 "이케아 포엥 암체어").
 * 서버 검색이 잠시 막히면 503 PRODUCT_SEARCH_UNAVAILABLE → 실패로 보지 않고 결과만 출력.
 * ./gradlew :app:testDebugUnitTest --tests "*ProductSearchApiLiveTest*"
 */
class ProductSearchApiLiveTest {

    @Test
    fun searchProducts_onRealServer() = runBlocking {
        assumeTrue("서버 계정이 local.properties에 없어 건너뜀", BuildConfig.CHAGOK_API_USERNAME.isNotBlank())
        val query = System.getenv("CHAGOK_TEST_PRODUCT_QUERY") ?: "이케아 포엥 암체어"

        val result = ProductRemoteRepository().search(query)
        assertTrue("서버에 닿지 못함: $result", result !is ApiResult.NetworkError)
        assertTrue("인증 실패: $result", (result as? ApiResult.Failure)?.httpCode != 401)
        when (result) {
            is ApiResult.Success -> {
                val candidates = ProductCandidateUi.sort(result.data.candidates.orEmpty().map(ProductCandidateUi::from))
                println("POST products/search \"$query\" → 후보 ${candidates.size}개")
                candidates.forEach {
                    println("  ${it.name} | ${it.detail} | ${it.transportState} | ${it.sizeLabel} | 치수 완전=${it.hasAllDimensions} | image=${it.imageUrl ?: "(없음 → 기본 이미지)"}")
                }
            }
            else -> println("POST products/search \"$query\" → $result")
        }
    }
}
