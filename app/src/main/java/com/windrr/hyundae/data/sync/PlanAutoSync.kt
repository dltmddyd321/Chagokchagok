package com.windrr.hyundae.data.sync

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.windrr.hyundae.data.local.AppDatabase
import com.windrr.hyundae.reminder.ArrivalReminderService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

/**
 * 최신 계획 자동 동기화.
 * 서버가 차량에 새 계획을 알려 주지 못해서, 사용자가 차를 쓰기 시작하는 순간마다 최신 계획 1개를 다시 받는다.
 * 계기: 앱 시작·화면 복귀·주기, 화면 터치, 운전석 문 열림, 기어 P → 주행.
 *
 * - 계기가 몰려도 [MIN_INTERVAL_MS]에 한 번만 서버에 묻는다 (앱 시작은 바로).
 * - 받은 결과는 기기 DB에 저장되고, 홈 카드는 DB를 보고 있어 자동으로 바뀐다.
 * - 최신 계획이 바뀌면 도착 감지(짐 챙기기 알림)를 새 계획으로 다시 건다.
 */
object PlanAutoSync {

    private const val TAG = "PlanAutoSync"

    /** 서버에 다시 묻는 최소 간격 */
    const val MIN_INTERVAL_MS = 30_000L

    private val mutex = Mutex()

    @Volatile
    private var lastRunAt = 0L

    /** 도착 감지를 걸어 둔 계획 */
    @Volatile
    private var monitoringPlanId: String? = null

    /** @param force true면 간격을 무시 (앱 시작) */
    suspend fun sync(context: Context, reason: String, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastRunAt < MIN_INTERVAL_MS) return
        // 이미 동기화 중이면 그 결과를 쓴다
        if (!mutex.tryLock()) return
        try {
            lastRunAt = now
            val app = context.applicationContext
            val changed = PlanSyncRepository(app).syncRecentPlans()
            Log.d(TAG, "동기화($reason): 갱신 ${changed ?: "실패"}")
            restartReminderIfNeeded(app)
        } finally {
            mutex.unlock()
        }
    }

    /** 최신 계획이 바뀌었으면(처음 포함) 도착 감지를 그 계획으로. 동기화 실패여도 기기에 있던 계획으로 건다 */
    private suspend fun restartReminderIfNeeded(context: Context) {
        val latestId = AppDatabase.getInstance(context).planDao().observePlans().first().firstOrNull()?.plan?.planId ?: return
        if (latestId == monitoringPlanId) return
        // 앱이 뒤에 있을 때는 포그라운드 서비스를 시작할 수 없어 실패할 수 있다 → 다음 계기에 다시
        runCatching { ArrivalReminderService.start(context, latestId) }
            .onSuccess { monitoringPlanId = latestId }
            .onFailure { Log.w(TAG, "도착 감지 시작 실패", it) }
    }
}
