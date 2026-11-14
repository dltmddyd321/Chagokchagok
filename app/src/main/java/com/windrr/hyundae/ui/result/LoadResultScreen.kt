package com.windrr.hyundae.ui.result

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.windrr.hyundae.R
import com.windrr.hyundae.ui.home.PleosNavy
import com.windrr.hyundae.ui.home.PleosTextPrimary
import com.windrr.hyundae.ui.home.PleosTextSecondary
import com.windrr.hyundae.ui.loadcheck.LoadItem
import kotlin.math.roundToInt

/**
 * 적재 결과 / 추천 배치 (시안).
 * 왼쪽: 결과 제목 · 추천 방법(배치안) · 가져갈 짐 · 닫기 / 이 조건으로 진행
 * 오른쪽: 배치 결과 — 계산 중엔 흐린 투싼 탑뷰 + "배치 확인 중", 결과가 나오면 고른 배치안을 그린다.
 */
@Composable
fun LoadResultRoute(
    input: LoadResultInput,
    onClose: () -> Unit,
    viewModel: LoadResultViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start(input) }
    BackHandler(onBack = onClose)
    LoadResultScreen(
        state = state,
        items = input.items,
        onSelect = viewModel::select,
        onRetry = viewModel::retry,
        onClose = onClose,
    )
}

@Composable
fun LoadResultScreen(
    state: LoadResultState,
    items: List<LoadItem>,
    onSelect: (Int) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // 왼쪽
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
        ) {
            Text(text = "적재 결과  /  추천 배치", color = PleosTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            ResultTitle(state)
            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (state is LoadResultState.Result && state.options.isNotEmpty()) {
                    Text(text = "추천 방법", color = PleosTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(14.dp))
                    state.options.forEachIndexed { index, option ->
                        OptionCard(option = option, selected = index == state.selectedIndex) { onSelect(index) }
                        Spacer(Modifier.height(12.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                }
                if (state is LoadResultState.Error) {
                    RetryCard(message = state.message, onRetry = onRetry)
                    Spacer(Modifier.height(20.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "가져갈 짐 ", color = PleosTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(text = "${items.sumOf { it.quantity }}", color = CountBlue, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(12.dp))
                // 고른 방법에서 싣지 못하는 짐은 "불가능" 표시
                val excluded = (state as? LoadResultState.Result)?.selected?.excludedNames.orEmpty()
                items.forEach { item ->
                    ItemRow(item, impossible = item.name in excluded)
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BottomButton(text = "닫기", primary = false, enabled = true, modifier = Modifier.width(150.dp), onClick = onClose)
                // TODO: "이 조건으로 진행" 다음 화면 시안 나오면 연결
                BottomButton(text = "이 조건으로 진행  →", primary = true, enabled = false, modifier = Modifier.weight(1f), onClick = {})
            }
        }

        // 오른쪽: 배치 결과
        ResultPanel(
            state = state,
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun ResultTitle(state: LoadResultState) {
    val (title, subtitle) = when (state) {
        LoadResultState.Loading -> buildAnnotatedString { append("적재 가능 여부를 확인하고 있어요") } to
            "가져갈 짐과 탑승 인원으로 적재 배치를 계산합니다"
        is LoadResultState.Result -> if (state.possible) {
            buildAnnotatedString {
                append("현재 조건은 ")
                withStyle(SpanStyle(color = PossibleBlue)) { append("적재 가능") }
            } to "추천 방법을 골라 배치를 확인해 보세요"
        } else {
            buildAnnotatedString {
                append("현재 조건은 ")
                withStyle(SpanStyle(color = ImpossibleRed)) { append("적재 불가능") }
            } to if (state.options.isEmpty()) "짐을 빼도 실을 수 있는 배치를 찾지 못했어요" else "현재 좌석으로는 짐을 함께 싣기 어렵습니다"
        }
        is LoadResultState.Error -> buildAnnotatedString { append("배치를 확인하지 못했어요") } to "잠시 후 다시 시도해 주세요"
    }
    Text(text = title, color = PleosTextPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
    Spacer(Modifier.height(10.dp))
    Text(text = subtitle, color = PleosTextSecondary, fontSize = 16.sp)
}

/** 추천 방법 한 줄: ○ 제목 / 부제 */
@Composable
private fun OptionCard(option: ArrangementOptionUi, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) SelectedBorder else CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) PleosNavy else RadioBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(PleosNavy),
                )
            }
        }
        Spacer(Modifier.width(20.dp))
        Column {
            Text(text = option.title, color = PleosTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(text = option.subtitle, color = PleosTextSecondary, fontSize = 16.sp)
        }
    }
}

@Composable
private fun RetryCard(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .padding(24.dp),
    ) {
        Text(text = message, color = PleosTextPrimary, fontSize = 17.sp)
        Spacer(Modifier.height(14.dp))
        BottomButton(text = "다시 시도", primary = true, enabled = true, modifier = Modifier.fillMaxWidth(), onClick = onRetry)
    }
}

/** 가져갈 짐 한 줄: 이미지 · 이름 · 치수 · 운반 상태 */
@Composable
private fun ItemRow(item: LoadItem, impossible: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(if (impossible) 1.5.dp else 1.dp, if (impossible) ImpossibleRed.copy(alpha = 0.6f) else CardBorder, shape)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painter = painterResource(R.drawable.ic_item_box), contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, color = PleosTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(
                text = listOfNotNull(item.sizeLabel, item.stateLabel, if (item.quantity > 1) "${item.quantity}개" else null).joinToString(" · "),
                color = PleosTextSecondary,
                fontSize = 14.sp,
            )
        }
        if (impossible) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(ImpossibleRed.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(text = "불가능", color = ImpossibleRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BottomButton(text: String, primary: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .height(80.dp)
            .clip(shape)
            .background(
                when {
                    !primary -> Brush.verticalGradient(listOf(Color.White, CloseBottom))
                    enabled -> Brush.verticalGradient(listOf(PrimaryTop, PrimaryBottom))
                    else -> Brush.verticalGradient(listOf(DisabledTop, DisabledBottom))
                },
            )
            .border(1.dp, if (primary) Color.Transparent else CardBorder, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !primary -> PleosNavy
                enabled -> Color.White
                else -> Color.White.copy(alpha = 0.8f)
            },
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** 오른쪽 남색 패널: "배치 결과" + 투싼 탑뷰 */
@Composable
private fun ResultPanel(state: LoadResultState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(PanelBg)
            .border(2.dp, PanelBorder, shape)
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        Text(text = "배치 결과", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PanelDivider),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val selected = (state as? LoadResultState.Result)?.selected
            val possible = (state as? LoadResultState.Result)?.possible == true
            TopView(layout = selected?.layout, dimmed = selected == null, placedFill = if (possible) ItemFill else AlternativeFill)
            // 짐 빼고 싣기: 못 싣는 짐은 위치가 없어서 탑뷰 위에 빨간 안내로
            if (selected != null && selected.excludedNames.isNotEmpty()) {
                Text(
                    text = "${selected.excludedNames.joinToString(", ")} 불가능 · 공간 부족",
                    color = ImpossibleRed,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PanelBg.copy(alpha = 0.75f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when (state) {
                LoadResultState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = SpinnerBlue,
                        trackColor = SpinnerTrack,
                        strokeWidth = 8.dp,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.size(132.dp),
                    )
                    Spacer(Modifier.height(36.dp))
                    Text(text = "배치 확인 중", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                }
                is LoadResultState.Error -> Text(
                    text = "적재 가능한 배치를\n찾지 못했어요",
                    color = Color.White,
                    fontSize = 28.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
                is LoadResultState.Result -> if (state.options.isEmpty()) {
                    Text(
                        text = "적재 가능한 배치를\n찾지 못했어요",
                        color = Color.White,
                        fontSize = 28.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 투싼 탑뷰. 배치안이 있으면 좌석 표시칸·접을 자리·짐 사각형을 올린다 */
@Composable
private fun TopView(layout: ArrangementLayoutUi?, dimmed: Boolean, placedFill: Color) {
    val image = ImageBitmap.imageResource(R.drawable.img_vehicle_tucson_top_view)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(VehicleTopView.CROP_W.toFloat() / VehicleTopView.CROP_H)
            .alpha(if (dimmed) 0.22f else 1f)
            .drawBehind {
                drawImage(
                    image = image,
                    srcOffset = IntOffset(VehicleTopView.CROP_LEFT, VehicleTopView.CROP_TOP),
                    srcSize = IntSize(VehicleTopView.CROP_W, VehicleTopView.CROP_H),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.High,
                )
            },
    ) {
        layout ?: return@BoxWithConstraints
        val scale = maxWidth / VehicleTopView.CROP_W.toFloat()
        layout.seats.forEach { seat ->
            OverlayBox(seat.rect, scale, if (seat.mark == SeatMark.BOARDING) BoardingFill else EmptySeatFill, if (seat.mark == SeatMark.BOARDING) "탑승" else "좌석")
        }
        layout.foldedGroups.forEach { group -> group.markRects.forEach { OverlayBox(it, scale, FoldedFill, "접기") } }
        layout.items.forEach { item -> OverlayBox(item.rect, scale, placedFill, item.name) }
    }
}

@Composable
private fun OverlayBox(rect: PxRect, scale: Dp, fill: Color, label: String) {
    Box(
        modifier = Modifier
            .offset(x = scale * (rect.left - VehicleTopView.CROP_LEFT), y = scale * (rect.top - VehicleTopView.CROP_TOP))
            .size((scale * rect.width).coerceAtLeast(20.dp), (scale * rect.height).coerceAtLeast(20.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(fill)
            .drawBehind {
                val inset = 1.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val CountBlue = Color(0xFF2E6BD6)
private val PossibleBlue = Color(0xFF1F63F2)
private val ImpossibleRed = Color(0xFFE02424)
private val CardBorder = Color(0xFFE4EAF3)
private val SelectedBorder = Color(0xFF6F9CE0)
private val RadioBorder = Color(0xFFB9C3D3)
private val CloseBottom = Color(0xFFEAF1FB)
private val PrimaryTop = Color(0xFF24477A)
private val PrimaryBottom = Color(0xFF14305A)
private val DisabledTop = Color(0xFFC9D6E8)
private val DisabledBottom = Color(0xFFB9C8DD)
private val PanelBg = Color(0xFF0B1A33)
private val PanelBorder = Color(0xFF3B82F6)
private val PanelDivider = Color(0xFF23406A)
private val SpinnerBlue = Color(0xFF5BB6FF)
private val SpinnerTrack = Color(0xFF2A3D5C)
private val BoardingFill = Color(0xCC5D6672)
private val EmptySeatFill = Color(0xB38C9199)
private val FoldedFill = Color(0x99202833)
private val ItemFill = Color(0xE62C5DA6)

/** 적재 불가 상태의 대안 배치 (시안: 청록 점선) */
private val AlternativeFill = Color(0xCC14544F)
