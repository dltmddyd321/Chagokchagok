package com.windrr.hyundae.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.windrr.hyundae.R
import com.windrr.hyundae.data.weather.WeatherInfo
import com.windrr.hyundae.data.weather.WeatherType

/** Pleos 홈: 인사 + 날씨 / 저장한 계획 카드 / 공유받은 배치 / 이 짐, 다 실릴까? */
@Composable
fun HomeRoute(
    onOpenPlans: () -> Unit,
    onEnterShareCode: () -> Unit,
    onCheckFit: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onOpenPlans = onOpenPlans,
        onEnterShareCode = onEnterShareCode,
        onCheckFit = onCheckFit,
        onRefreshWeather = { viewModel.onIntent(HomeIntent.RefreshWeather) },
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenPlans: () -> Unit,
    onEnterShareCode: () -> Unit,
    onCheckFit: () -> Unit,
    onRefreshWeather: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 28.dp),
    ) {
        VehicleStatusBar(gearLabel = state.gearLabel, trunkLabel = state.trunkLabel)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "안녕하세요. ${state.userName}님!",
                    color = PleosTextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "차에 실을 제품을 검색하거나, 모바일에서 미리 저장한 배치를 불러올 수 있어요",
                    color = PleosTextSecondary,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.width(24.dp))
            WeatherCard(weather = state.weather, loading = state.weatherLoading, onClick = onRefreshWeather)
        }
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SavedPlansCard(
                planCount = state.planCount,
                onOpenPlans = onOpenPlans,
                modifier = Modifier
                    .weight(1.37f)
                    .fillMaxHeight(),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SharedPlanCard(
                    onEnterShareCode = onEnterShareCode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                CheckFitCard(
                    onClick = onCheckFit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.1f),
                )
            }
        }
    }
}

/** 오른쪽 위: "P · 주차 중"  [트렁크 닫힘] — 차량 신호가 없으면 숨김 */
@Composable
private fun VehicleStatusBar(gearLabel: String?, trunkLabel: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        gearLabel?.let {
            Text(text = it, color = PleosTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(24.dp))
        }
        trunkLabel?.let {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChipBg)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_car_outline),
                    contentDescription = null,
                    tint = PleosTextPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = it, color = PleosTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 날씨 카드: 구름 아이콘 · "현재 위치 · 성동구 / 흐림" | "18°C / 외기". 누르면 새로고침 */
@Composable
private fun WeatherCard(weather: WeatherInfo?, loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeatherIcon(type = weather?.conditionType ?: WeatherType.CLOUDY, modifier = Modifier.size(width = 52.dp, height = 40.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = weather?.district?.let { "현재 위치 · $it" } ?: "현재 위치",
                color = PleosTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    weather != null -> weather.condition
                    loading -> "불러오는 중"
                    else -> "날씨 정보 없음"
                },
                color = PleosTextSecondary,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(18.dp))
        Box(
            Modifier
                .width(1.dp)
                .height(40.dp)
                .background(DividerColor),
        )
        Spacer(Modifier.width(18.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (weather == null && loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = PleosNavy, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = weather?.let { "${it.temperatureC}°C" } ?: "--°C",
                    color = PleosTextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Text(text = "외기", color = PleosTextSecondary, fontSize = 12.sp)
        }
    }
}

/** 임시 날씨 아이콘 (구름·해·비) — 아이콘 에셋 받으면 교체 */
@Composable
private fun WeatherIcon(type: WeatherType, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (type == WeatherType.CLEAR || type == WeatherType.PARTLY_CLOUDY) {
            drawCircle(SunColor, radius = h * 0.28f, center = Offset(w * 0.68f, h * 0.32f))
        }
        if (type != WeatherType.CLEAR) {
            val cloud = Brush.verticalGradient(listOf(Color.White, CloudShade))
            drawCircle(cloud, radius = h * 0.26f, center = Offset(w * 0.38f, h * 0.52f))
            drawCircle(cloud, radius = h * 0.33f, center = Offset(w * 0.58f, h * 0.42f))
            drawRoundRect(
                cloud,
                topLeft = Offset(w * 0.12f, h * 0.5f),
                size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.3f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.15f),
            )
        }
        if (type == WeatherType.RAIN || type == WeatherType.THUNDER) {
            listOf(0.3f, 0.5f, 0.7f).forEach { x ->
                drawLine(RainColor, Offset(w * x, h * 0.86f), Offset(w * x - 3f, h), strokeWidth = 3f)
            }
        }
    }
}

/** 왼쪽 큰 카드: 트렁크 열린 아반떼 배경 + 저장한 배치 계획 + 내 계획보기 */
@Composable
private fun SavedPlansCard(planCount: Int, onOpenPlans: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .shadow(10.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape),
    ) {
        Image(
            painter = painterResource(R.drawable.img_banner_avante_coastal),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomCenter,
            modifier = Modifier.matchParentSize(),
        )
        // 위쪽 글씨가 잘 보이도록 어둡게
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(BannerShadeTop, Color.Transparent))),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
        ) {
            Text(text = "차곡차곡", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (planCount > 0) "저장한 배치 계획이\n${planCount}개 있어요" else "저장한 배치 계획이\n없습니다",
                color = Color.White,
                fontSize = 34.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "모바일에서 저장한 계획을\n차량에서 확인할 수 있습니다",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.weight(1f))
            // 반투명 유리 버튼
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenPlans),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "내 계획보기", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 오른쪽 위 카드: 공유받은 배치 (아이오닉5 배경) + 공유 코드 입력 */
@Composable
private fun SharedPlanCard(onEnterShareCode: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .shadow(10.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape),
    ) {
        Image(
            painter = painterResource(R.drawable.img_shared_ioniq5),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
        ) {
            Text(text = "공유받은 배치", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Text(text = "일행에게 받은 공유 코드를 입력합니다", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .width(220.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable(onClick = onEnterShareCode)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_code),
                    contentDescription = null,
                    tint = PleosNavy,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "공유 코드 입력",
                    color = PleosNavy,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** 오른쪽 아래 카드: 이 짐, 다 실릴까? (밝은 카드 + 캐리어 이미지) */
@Composable
private fun CheckFitCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .shadow(8.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color.White, CheckFitTint)))
            .border(1.dp, Color.White, shape)
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(R.drawable.img_item_suitcase_travel_bag),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
                .fillMaxWidth(0.5f)
                .fillMaxHeight(0.55f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_arrow_forward),
            contentDescription = "이동",
            tint = ArrowColor,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(28.dp)
                .size(32.dp),
        )
        Column(modifier = Modifier.padding(28.dp)) {
            Text(text = "이 짐, 다 실릴까?", color = PleosNavy, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "짐과 탑승 인원에 맞는\n적재 방법을 확인합니다",
                color = PleosTextSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

internal val PleosNavy = Color(0xFF16305A)
internal val PleosTextPrimary = Color(0xFF111A2E)
internal val PleosTextSecondary = Color(0xFF5A6273)
private val ChipBg = Color(0xFFE6EBF3)
private val DividerColor = Color(0xFFE1E5EC)
private val ShadowColor = Color(0x331B2B4B)
private val BannerShadeTop = Color(0xCC0F2140)
private val CheckFitTint = Color(0xFFE3EBF8)
private val ArrowColor = Color(0xFF9AB0D0)
private val SunColor = Color(0xFFFFC94D)
private val CloudShade = Color(0xFFC9D6EA)
private val RainColor = Color(0xFF6E9BE0)

@Preview(widthDp = 1280, heightDp = 800, showBackground = true, backgroundColor = 0xFFEFF2F8)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        state = HomeUiState(
            vehicleDesc = "5인승 SUV",
            weather = WeatherInfo("성동구", "흐림", WeatherType.CLOUDY, 18),
            weatherLoading = false,
            gear = ai.pleos.playground.vehicle.constant.control.VehicleGear.GEAR_PARK,
            trunkOpen = false,
        ),
        onOpenPlans = {},
        onEnterShareCode = {},
        onCheckFit = {},
        onRefreshWeather = {},
    )
}
