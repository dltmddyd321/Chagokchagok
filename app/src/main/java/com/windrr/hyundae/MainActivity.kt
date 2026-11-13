package com.windrr.hyundae

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.windrr.hyundae.data.local.AppDatabase
import com.windrr.hyundae.reminder.ArrivalReminderService
import com.windrr.hyundae.speech.SpeechInputScreen
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
                RequestNotificationPermission()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SpeechInputScreen(modifier = Modifier.padding(innerPadding))
                }
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

/** 도착·주차 리마인드 알림용 (Android 13+) */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
