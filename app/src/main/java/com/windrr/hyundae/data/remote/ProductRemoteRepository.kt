package com.windrr.hyundae.data.remote

import com.windrr.hyundae.data.remote.model.ProductSearchRequest
import com.windrr.hyundae.data.remote.model.ProductSearchResponse
import java.util.UUID

/** 제품 규격 검색 */
class ProductRemoteRepository(private val api: ChagokApi = ApiClient.chagokApi) {

    /**
     * 새 검색은 새 [idempotencyKey]. 통신 오류로 같은 검색을 다시 보낼 때만 같은 키.
     * 서버가 검색을 못 쓰면 503 PRODUCT_SEARCH_UNAVAILABLE (ApiResult.Failure).
     */
    suspend fun search(
        query: String,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): ApiResult<ProductSearchResponse> =
        apiCall { api.searchProducts(idempotencyKey, ProductSearchRequest(query = query.trim().take(MAX_QUERY_LENGTH))) }

    private companion object {
        const val MAX_QUERY_LENGTH = 300
    }
}
