package com.windrr.hyundae.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.windrr.hyundae.R
import com.windrr.hyundae.speech.SpeechInputScreen
import com.windrr.hyundae.ui.home.HomeIntent
import com.windrr.hyundae.ui.home.HomeRoute
import com.windrr.hyundae.ui.home.HomeViewModel
import com.windrr.hyundae.ui.loadcheck.LoadCheckRoute

/** 왼쪽 사이드 메뉴 */
enum class PleosTab(val label: String, @DrawableRes val iconRes: Int) {
    HOME("홈", R.drawable.ic_nav_home),
    PLANS("내 계획", R.drawable.ic_nav_plan),
    QUICK("빠른 확인", R.drawable.ic_nav_mic),
}

/** Pleos 앱 전체 틀: 왼쪽 사이드 메뉴 + 오른쪽 탭 내용 */
@Composable
fun PleosMainScreen() {
    var tab by rememberSaveable { mutableStateOf(PleosTab.HOME) }
    // 짐과 탑승 인원 화면 (null = 홈). 들어올 때마다 새 값 → 입력 초기화
    var loadCheckId by rememberSaveable { mutableStateOf<Long?>(null) }
    val homeViewModel: HomeViewModel = viewModel()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()

    RequestStartupPermissions(onLocationGranted = { homeViewModel.onIntent(HomeIntent.RefreshWeather) })

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(BackgroundTop, BackgroundBottom))),
    ) {
        NavRail(
            selected = tab,
            onSelect = {
                tab = it
                loadCheckId = null
            },
            vehicleDesc = homeState.vehicleDesc,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 20.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            // 모든 화면 공통: 오른쪽 위 차량 상태 (Vehicle SDK 기어·트렁크)
            VehicleStatusBar(
                gearLabel = homeState.gearLabel,
                trunkLabel = homeState.trunkLabel,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 12.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    PleosTab.HOME -> if (loadCheckId != null) {
                        LoadCheckRoute(
                            onBack = { loadCheckId = null },
                            // TODO: 서버 배치 계산 → 적재 결과 화면 연결
                            onRequestCheck = { _, _ -> },
                            viewModel = viewModel(key = "load-check-$loadCheckId"),
                        )
                    } else {
                        HomeRoute(
                            onOpenPlans = { tab = PleosTab.PLANS },
                            // TODO: 공유 코드 입력 화면 시안 나오면 연결
                            onEnterShareCode = {},
                            // "이 짐, 다 실릴까?" 배너 → 짐과 탑승 인원 확인
                            onCheckFit = { loadCheckId = System.currentTimeMillis() },
                            viewModel = homeViewModel,
                        )
                    }
                    PleosTab.PLANS -> ComingSoon("내 계획 (준비 중)")
                    PleosTab.QUICK -> SpeechInputScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun NavRail(
    selected: PleosTab,
    onSelect: (PleosTab) -> Unit,
    vehicleDesc: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(100.dp)
            .fillMaxHeight()
            .shadow(6.dp, RoundedCornerShape(24.dp), ambientColor = RailShadow, spotColor = RailShadow)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(vertical = 22.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 임시 로고: 로고 에셋 받으면 교체
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(LogoTop, LogoBottom))),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "ㅊ", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(20.dp))
        PleosTab.entries.forEach { tab ->
            NavItem(tab = tab, selected = tab == selected, onClick = { onSelect(tab) })
        }
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.img_profile_minjeong),
            contentDescription = "프로필",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "내 차", color = RailTextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = vehicleDesc.ifBlank { "-" },
                color = RailTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 선택 = 남색 그라데이션 타일 + 흰 아이콘·글자, 미선택 = 연한 타일 + 남색 */
@Composable
private fun NavItem(tab: PleosTab, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .size(width = 76.dp, height = 76.dp)
            .clip(shape)
            .background(
                if (selected) {
                    Brush.verticalGradient(listOf(NavSelectedTop, NavSelectedBottom))
                } else {
                    Brush.verticalGradient(listOf(NavIdle, NavIdle))
                },
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(tab.iconRes),
            contentDescription = null,
            tint = if (selected) Color.White else NavIdleContent,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = tab.label,
            color = if (selected) Color.White else NavIdleContent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ComingSoon(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = RailTextSecondary, fontSize = 18.sp)
    }
}

/** 시작 시 권한: 위치(날씨용, Pleos FusedLocation) + 알림(도착 리마인드, Android 13+) */
@Composable
private fun RequestStartupPermissions(onLocationGranted: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[LOCATION_PERMISSION] == true) onLocationGranted()
    }
    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(LOCATION_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) launcher.launch(wanted.toTypedArray())
    }
}

private const val LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"

private val BackgroundTop = Color(0xFFF4F6FA)
private val BackgroundBottom = Color(0xFFE6ECF5)
private val RailShadow = Color(0x221B2B4B)
private val LogoTop = Color(0xFF1E3A66)
private val LogoBottom = Color(0xFF13284A)
private val NavSelectedTop = Color(0xFF2F5A8C)
private val NavSelectedBottom = Color(0xFF1C3E6A)
private val NavIdle = Color(0xFFF1F4F9)
private val NavIdleContent = Color(0xFF2B4A78)
private val RailTextPrimary = Color(0xFF111A2E)
private val RailTextSecondary = Color(0xFF5A6273)
