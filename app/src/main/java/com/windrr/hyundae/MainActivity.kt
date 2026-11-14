package com.windrr.hyundae

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.windrr.hyundae.data.sync.PlanAutoSync
import com.windrr.hyundae.push.PushNotifier
import com.windrr.hyundae.push.PushTokenSync
import com.windrr.hyundae.ui.main.PleosMainScreen
import com.windrr.hyundae.ui.theme.HyundaeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        keepPlanInSync()
        setUpPush()
        setContent {
            HyundaeTheme {
                // 화면 어디를 터치하든 최신 계획을 다시 받는다 (터치는 가로채지 않고 지켜보기만)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press) {
                                        lifecycleScope.launch { PlanAutoSync.sync(applicationContext, "터치") }
                                    }
                                }
                            }
                        },
                ) {
                    // 홈(날씨·차량 상태·계획) + 사이드 메뉴. 위치·알림 권한은 화면 안에서 요청
                    PleosMainScreen()
                }
            }
        }
    }

    /** FCM: 알림 채널 생성 + 토큰 받아 저장·서버 등록. 알림 권한은 PleosMainScreen 시작 시 요청 */
    private fun setUpPush() {
        PushNotifier.createChannel(this)
        lifecycleScope.launch { PushTokenSync.refresh(applicationContext) }
    }

    /**
     * 최신 계획 동기화 (서브앱에서 새 계획을 저장하면 차에 바로 알려 줄 수 없어서 앱이 먼저 묻는다).
     * - 앱 시작: 바로 동기화 → 최신 계획으로 도착 감지 시작 (실패하면 기기에 있던 계획으로)
     * - 화면에 보이는 동안: 다시 나타날 때 + 1분마다 (간격 제한은 PlanAutoSync가 처리)
     * 터치·운전석 문·기어 계기는 setContent의 터치 감지와 HomeViewModel에서.
     */
    private fun keepPlanInSync() {
        lifecycleScope.launch { PlanAutoSync.sync(applicationContext, "앱 시작", force = true) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    PlanAutoSync.sync(applicationContext, "화면 표시·주기")
                    delay(PERIODIC_SYNC_MS)
                }
            }
        }
    }

    private companion object {
        const val PERIODIC_SYNC_MS = 60_000L
    }
}
