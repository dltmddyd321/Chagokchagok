package com.windrr.hyundae.speech

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.windrr.hyundae.BuildConfig

/**
 * 디버그 빌드 전용: 마이크 소리가 안 들어오는 에뮬레이터에서 말한 것처럼 넣기 (시연 녹화용).
 * adb shell am broadcast -a com.windrr.hyundae.DEBUG_SPEECH --es text "이케아 락캅텐 아딜스"
 * 출시(release) 빌드에는 등록되지 않는다.
 */
@Composable
fun DebugSpeechInput(onText: (String) -> Unit) {
    if (!BuildConfig.DEBUG) return
    val context = LocalContext.current
    val latest by rememberUpdatedState(onText)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.getStringExtra("text")?.takeIf { it.isNotBlank() }?.let(latest)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(DEBUG_SPEECH_ACTION), ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
}

private const val DEBUG_SPEECH_ACTION = "com.windrr.hyundae.DEBUG_SPEECH"
