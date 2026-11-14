package com.windrr.hyundae

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.windrr.hyundae.data.local.AppDatabase
import com.windrr.hyundae.reminder.ArrivalReminderService
import com.windrr.hyundae.ui.main.PleosMainScreen
import com.windrr.hyundae.ui.theme.HyundaeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startArrivalReminderForLatestPlan()
        setContent {
            HyundaeTheme {
                // 홈(날씨·차량 상태·계획) + 사이드 메뉴. 위치·알림 권한은 화면 안에서 요청
                PleosMainScreen()
            }
        }
    }

    private fun startArrivalReminderForLatestPlan() {
        lifecycleScope.launch {
            val latestPlan = AppDatabase.getInstance(this@MainActivity).planDao().observePlans().first().firstOrNull()
            latestPlan?.let { ArrivalReminderService.start(this@MainActivity, it.plan.planId) }
        }
    }
}
