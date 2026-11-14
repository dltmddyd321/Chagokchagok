package com.windrr.hyundae

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.windrr.hyundae.data.local.AppDatabase
import com.windrr.hyundae.data.sync.PlanSyncRepository
import com.windrr.hyundae.push.PushNotifier
import com.windrr.hyundae.push.PushTokenSync
import com.windrr.hyundae.reminder.ArrivalReminderService
import com.windrr.hyundae.ui.main.PleosMainScreen
import com.windrr.hyundae.ui.theme.HyundaeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        syncPlansThenStartArrivalReminder()
        setUpPush()
        setContent {
            HyundaeTheme {
                // 홈(날씨·차량 상태·계획) + 사이드 메뉴. 위치·알림 권한은 화면 안에서 요청
                PleosMainScreen()
            }
        }
    }

    /** FCM: 알림 채널 생성 + 토큰 받아 저장·서버 등록. 알림 권한은 PleosMainScreen 시작 시 요청 */
    private fun setUpPush() {
        PushNotifier.createChannel(this)
        lifecycleScope.launch { PushTokenSync.refresh(applicationContext) }
    }

    /**
     * 서버 계획·짐 목록을 기기 DB로 받은 뒤, 가장 최근 계획으로 도착 감지를 시작한다.
     * 동기화가 실패해도(오프라인 등) 기기에 있던 계획으로 시작한다.
     */
    private fun syncPlansThenStartArrivalReminder() {
        lifecycleScope.launch {
            PlanSyncRepository(applicationContext).syncRecentPlans()
            val latestPlan = AppDatabase.getInstance(this@MainActivity).planDao().observePlans().first().firstOrNull()
            latestPlan?.let { ArrivalReminderService.start(this@MainActivity, it.plan.planId) }
        }
    }
}
