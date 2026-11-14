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
import androidx.compose.ui.text.style.TextOverflow
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
            .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 28.dp),
    ) {
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
            LatestPlanCard(
                plan = state.latestPlan,
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
        WeatherIcon(type = weather?.conditionType ?: WeatherType.CLOUDY, modifier = Modifier.size(width = 50.dp, height = 46.dp))
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

/** 날씨 아이콘 (디자인 리소스 7종, 77×70 비율) */
@Composable
private fun WeatherIcon(type: WeatherType, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(type.iconRes()),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/** WMO 코드 묶음(WeatherType) → 아이콘. 묶는 기준은 WeatherRepository.describe 참고 */
private fun WeatherType.iconRes(): Int = when (this) {
    WeatherType.CLEAR -> R.drawable.ic_weather_clear
    WeatherType.PARTLY_CLOUDY -> R.drawable.ic_weather_partly_cloudy
    WeatherType.CLOUDY -> R.drawable.ic_weather_cloudy
    WeatherType.FOG -> R.drawable.ic_weather_fog
    WeatherType.RAIN -> R.drawable.ic_weather_rain
    WeatherType.SNOW -> R.drawable.ic_weather_snow
    WeatherType.THUNDER -> R.drawable.ic_weather_thunder
}

/**
 * 왼쪽 큰 카드: 최신 계획 (호숫가 투싼 배경).
 * 카테고리 칩 · 계획 이름 · 안내 + 아래 유리 패널에 탑승 인원 / 가져갈 짐. 누르면 내 계획.
 * 계획이 없으면 모바일에서 저장하라는 안내.
 */
@Composable
private fun LatestPlanCard(plan: LatestPlanUi?, onOpenPlans: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .shadow(10.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .clickable(onClick = onOpenPlans),
    ) {
        Image(
            painter = painterResource(R.drawable.img_banner_weekend_outing_lakeside),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.matchParentSize(),
        )
        // 위 글씨·아래 패널이 잘 보이도록 위아래를 남색으로
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(BannerShadeTop, Color.Transparent, Color.Transparent, BannerShadeBottom))),
        )
        Icon(
            painter = painterResource(R.drawable.ic_arrow_forward),
            contentDescription = "내 계획 보기",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = 32.dp)
                .size(40.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
        ) {
            if (plan != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(CategoryChipBg)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(text = plan.categoryLabel, color = PleosNavy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    text = plan.name,
                    color = Color.White,
                    fontSize = 44.sp,
                    lineHeight = 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                Text(text = "함께 타는 사람과 가져갈 짐을 확인하세요", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                PlanSummaryPanel(plan)
            } else {
                Text(
                    text = "저장한 배치 계획이\n없어요",
                    color = Color.White,
                    fontSize = 34.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "모바일에서 계획을 저장하면\n차량에서 바로 확인할 수 있어요",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

/** 최신 계획 카드 아래 유리 패널: 탑승 인원 | 가져갈 짐 */
@Composable
private fun PlanSummaryPanel(plan: LatestPlanUi) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.78f))
            .border(1.dp, Color.White.copy(alpha = 0.6f), shape)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = "탑승 인원", color = PleosTextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(text = "총 ${plan.passengerCount}명", color = PleosTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(28.dp))
        Box(
            Modifier
                .width(1.dp)
                .height(52.dp)
                .background(PanelDivider),
        )
        Spacer(Modifier.width(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "가져갈 짐", color = PleosTextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = plan.itemsLine,
                color = PleosTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
private val DividerColor = Color(0xFFE1E5EC)
private val ShadowColor = Color(0x331B2B4B)
private val BannerShadeTop = Color(0xE60F2140)
private val BannerShadeBottom = Color(0xF00B1A36)
private val CategoryChipBg = Color(0xFFDCE6F7)
private val PanelDivider = Color(0xFFB7C0CF)
private val CheckFitTint = Color(0xFFE3EBF8)
private val ArrowColor = Color(0xFF9AB0D0)

@Preview(widthDp = 1280, heightDp = 800, showBackground = true, backgroundColor = 0xFFEFF2F8)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        state = HomeUiState(
            vehicleDesc = "5인승 SUV",
            weather = WeatherInfo("성동구", "흐림", WeatherType.CLOUDY, 18),
            weatherLoading = false,
            latestPlan = LatestPlanUi("p1", "여행", "주말 나들이", 3, "유모차 1개 · 캐리어 1개 외 3개"),
            gear = ai.pleos.playground.vehicle.constant.control.VehicleGear.GEAR_PARK,
            trunkOpen = false,
        ),
        onOpenPlans = {},
        onEnterShareCode = {},
        onCheckFit = {},
        onRefreshWeather = {},
    )
}
