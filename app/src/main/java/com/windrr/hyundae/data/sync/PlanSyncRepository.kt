package com.windrr.hyundae.data.sync

import android.content.Context
import android.util.Log
import com.windrr.hyundae.data.local.AppDatabase
import com.windrr.hyundae.data.remote.ApiResult
import com.windrr.hyundae.data.remote.PlanRemoteRepository
import com.windrr.hyundae.data.remote.model.PlanResponse
import com.windrr.hyundae.data.remote.model.PlanSummaryResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 서버 계획(짐 목록 포함)을 기기 DB에 미리 받아 둔다.
 * 주행 중 통신이 끊겨도 도착 알림·적재 확인이 기기 DB만으로 동작하게 하려는 것.
 *
 * - 주행 중 서비스라 가장 최근 계획 하나만 받는다. 목록(GET /plans?limit=1)으로 최신 계획을 받고, 계획마다 상세(GET /plans/{id})로 짐 크기까지 받는다.
 * - 내용이 같으면 건너뛴다. 서버에 없는 기기 계획은 지우지 않는다.
 */
class PlanSyncRepository(
    context: Context,
    private val remote: PlanRemoteRepository = PlanRemoteRepository(),
) {
    private val db = AppDatabase.getInstance(context)

    /** @return 새로 저장·갱신한 계획 수. 목록 조회부터 실패하면 null */
    suspend fun syncRecentPlans(limit: Int = SYNC_LIMIT): Int? = coroutineScope {
        val list = when (val result = remote.recentPlans(limit)) {
            is ApiResult.Success -> result.data.plans.orEmpty()
            else -> {
                Log.w(TAG, "계획 목록 동기화 실패: $result")
                return@coroutineScope null
            }
        }
        val permits = Semaphore(DETAIL_PARALLELISM)
        list.map { summary ->
            async {
                permits.withPermit {
                    when (val detail = remote.plan(summary.planId)) {
                        is ApiResult.Success -> save(summary, detail.data)
                        else -> {
                            Log.w(TAG, "계획 상세 동기화 실패 ${summary.planId}: $detail")
                            false
                        }
                    }
                }
            }
        }.awaitAll().count { it }.also { Log.d(TAG, "계획 동기화: 서버 ${list.size}개 중 $it 개 저장·갱신") }
    }

    private suspend fun save(summary: PlanSummaryResponse, detail: PlanResponse): Boolean {
        val local = PlanSyncMapper.toLocal(summary, detail)
        if (PlanSyncMapper.isSame(db.planDao().getPlan(local.plan.planId), local)) return false
        if (db.vehicleDao().get(local.plan.vehicleId) == null) {
            db.vehicleDao().upsert(PlanSyncMapper.vehicleFor(summary, local.plan))
        }
        db.planDao().savePlan(local.plan, local.items, local.carSeats)
        return true
    }

    private companion object {
        const val TAG = "PlanSync"
        /** Pleos는 최신 계획 하나만 쓴다 */
        const val SYNC_LIMIT = 1
        const val DETAIL_PARALLELISM = 4
    }
}
