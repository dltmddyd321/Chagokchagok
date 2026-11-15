package com.windrr.hyundae.data.navi

import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.helper.NaviHelper
import android.content.Context
import android.util.Log

/**
 * 차량 내비게이션 화면을 앞으로 띄운다 (NaviHelper.requestNavigationScreen(Foreground), 권한 NAVI_CUSTOM_MAP).
 * 우리 앱은 닫지 않는다. 화면을 나눠 보여 주는 비율(1:1, 1:1:1)은 Pleos 시스템이 정하고,
 * 우리 화면은 좁아져도 깨지지 않게 반응형 레이아웃으로 그린다.
 */
object NaviLauncher {

    private const val TAG = "NaviLauncher"

    fun openMap(context: Context) {
        val helper = NaviHelper(context.applicationContext)
        helper.initialize(
            {
                runCatching { helper.requestNavigationScreen(NavigationScreenRequest.Foreground) }
                    .onFailure { Log.w(TAG, "내비 화면 요청 실패", it) }
                helper.release()
            },
            {
                Log.w(TAG, "NaviHelper 초기화 실패", it)
                helper.release()
            },
        )
    }
}
