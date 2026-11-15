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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import com.windrr.hyundae.ui.loadcheck.ProductImage
import kotlin.math.roundToInt

/**
 * 적재 결과 / 추천 배치 (시안).
 * 왼쪽: 결과 제목 · 추천 방법(배치안) · 가져갈 짐 · 닫기 / 이 조건으로 진행
 * 오른쪽: 배치 결과 — 계산 중엔 흐린 투싼 탑뷰 + "배치 확인 중", 결과가 나오면 고른 배치안을 그린다.
 */
@Composable
fun LoadResultRoute(
    input: LoadResultInput,
    /** 차량 트렁크 열림 여부 (Vehicle SDK). 가이드 화면에서 열렸다가 닫히면 적재 완료 */
    trunkOpen: Boolean?,
    /** 적재 완료 > 지도 열기 */
    onOpenMap: () -> Unit,
    onClose: () -> Unit,
    /** 적재 가이드에서 "짐을 다 실었어요" */
    onFinished: () -> Unit,
    viewModel: LoadResultViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start(input) }
    val result = state as? LoadResultState.Result
    // 가이드 화면에서 뒤로 → 결과 화면, 결과 화면에서 뒤로 → 짐과 탑승 인원
    BackHandler {
        when {
            result?.completion != null -> onFinished()
            result?.proceeding == true -> viewModel.backToResult()
            else -> onClose()
        }
    }
    if (result != null && result.completion != null && result.selected != null) {
        LoadCompleteScreen(
            option = result.selected!!,
            items = input.items,
            completion = result.completion,
            onGoHome = onFinished,
            onOpenMap = onOpenMap,
        )
    } else if (result != null && result.proceeding && result.selected != null) {
        // 트렁크가 "열렸다가 닫히면" 적재 완료. 가이드에 들어올 때 이미 닫혀 있던 건 완료로 보지 않는다
        var trunkWasOpen by remember { mutableStateOf(false) }
        LaunchedEffect(trunkOpen) {
            when (trunkOpen) {
                true -> trunkWasOpen = true
                false -> if (trunkWasOpen) viewModel.complete(LoadCompletion.TRUNK_CLOSED)
                null -> Unit
            }
        }
        LoadGuideScreen(
            option = result.selected!!,
            steps = result.selectedGuide,
            onChangeCondition = viewModel::backToResult,
            onFinished = { viewModel.complete(LoadCompletion.BUTTON) },
        )
    } else {
        LoadResultScreen(
            state = state,
            items = input.items,
            onSelect = viewModel::select,
            onRetry = viewModel::retry,
            onClose = onClose,
            onProceed = { viewModel.proceed() },
        )
    }
}

@Composable
fun LoadResultScreen(
    state: LoadResultState,
    items: List<LoadItem>,
    onSelect: (Int) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onProceed: (ArrangementOptionUi) -> Unit = {},
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
                val chosen = (state as? LoadResultState.Result)?.selected
                BottomButton(text = "이 조건으로 진행  →", primary = true, enabled = chosen != null, modifier = Modifier.weight(1f)) {
                    chosen?.let(onProceed)
                }
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
                append("현재 조건으로 ")
                withStyle(SpanStyle(color = PossibleGreen)) { append("적재 가능") }
            } to "현재 조건으로 적재가 가능합니다"
        } else {
            buildAnnotatedString {
                append("현재 조건은 ")
                withStyle(SpanStyle(color = ImpossibleRed)) { append("적재 불가능") }
            } to if (state.options.isEmpty()) "짐을 빼도 실을 수 있는 배치를 찾지 못했어요" else "현재 좌석으로는 짐을 함께 싣기 어렵습니다"
        }
        is LoadResultState.Error -> if (state.timedOut) {
            buildAnnotatedString { append("제한 시간 안에 배치안을 찾지 못했어요") } to "짐이 많으면 계산이 오래 걸려요. 다시 시도하거나 짐을 줄여 보세요"
        } else {
            buildAnnotatedString { append("배치를 확인하지 못했어요") } to "잠시 후 다시 시도해 주세요"
        }
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
            .background(if (selected) SelectedBg else Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) SelectedBorder else CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) RadioBlue else RadioBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(RadioBlue),
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
        ProductImage(url = item.imageUrl, fallbackSize = 56.dp, modifier = Modifier.size(56.dp))
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

/**
 * 적재 가이드 (시안): 왼쪽 "OO를 안내대로 실어 주세요" + 단계 카드(서버 안내가 오면), 오른쪽 선택한 적재 배치.
 * 안내가 아직 오지 않았으면 카드 자리에 "가이드를 만들고 있어요".
 */
@Composable
fun LoadGuideScreen(
    option: ArrangementOptionUi,
    steps: List<GuideStepUi>?,
    onChangeCondition: () -> Unit,
    onFinished: () -> Unit,
) {
    val names = Josa.joinNames(option.layout.items.map { it.name }.distinct())
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
        ) {
            Text(text = "적재 진행  /  적재 가이드", color = PleosTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "$names${Josa.을를(names)}\n안내대로 실어 주세요",
                color = PleosTextPrimary,
                fontSize = 34.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(text = option.subtitle, color = PleosTextSecondary, fontSize = 16.sp)
            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                GuideStepsCard(steps)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BottomButton(text = "조건 변경", primary = false, enabled = true, modifier = Modifier.weight(0.42f), onClick = onChangeCondition)
                BottomButton(text = "✓  짐을 다 실었어요", primary = true, enabled = true, modifier = Modifier.weight(0.58f), onClick = onFinished)
            }
        }

        // 오른쪽: 선택한 적재 배치
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .clip(shape)
                .background(PanelBg)
                .padding(horizontal = 32.dp, vertical = 28.dp),
        ) {
            Text(text = "선택한 적재 배치", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
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
                TopView(layout = option.layout, dimmed = false)
            }
        }
    }
}

/**
 * 적재 완료 (시안): "적재가 완료되었습니다", 출발 전 확인 카드, 가져갈 짐, 홈으로 / 지도 열기.
 * 오른쪽은 최종 적재 배치도.
 */
@Composable
fun LoadCompleteScreen(
    option: ArrangementOptionUi,
    items: List<LoadItem>,
    completion: LoadCompletion,
    onGoHome: () -> Unit,
    onOpenMap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(0.48f)
                .fillMaxHeight(),
        ) {
            Text(text = "적재 진행  /  적재 완료", color = PleosTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text(text = "적재가 완료되었습니다", color = PleosTextPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (completion == LoadCompletion.TRUNK_CLOSED) "트렁크가 닫혔습니다" else "짐을 모두 실었습니다",
                color = PleosTextSecondary,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(28.dp))
            DepartureCheckCard()
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "가져갈 짐 ", color = PleosTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(text = "${items.sumOf { it.quantity }}", color = CountBlue, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items.forEach { ItemRow(it, impossible = false) }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BottomButton(text = "홈으로", primary = false, enabled = true, modifier = Modifier.weight(1f), onClick = onGoHome)
                BottomButton(text = "⌖  지도 열기", primary = true, enabled = true, modifier = Modifier.weight(1f), onClick = onOpenMap)
            }
        }

        // 오른쪽: 최종 적재 배치도
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = Modifier
                .weight(0.52f)
                .fillMaxHeight()
                .padding(top = 60.dp)
                .clip(shape)
                .background(PanelBg)
                .padding(horizontal = 32.dp, vertical = 28.dp),
        ) {
            Text(text = "최종 적재 배치도", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
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
                TopView(layout = option.layout, dimmed = false, showArrows = false)
            }
        }
    }
}

/** 출발 전 확인 (연한 파랑 카드 + ⓘ) */
@Composable
private fun DepartureCheckCard() {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(InfoTop, InfoBottom)))
            .border(1.dp, InfoBorder, shape)
            .padding(horizontal = 28.dp, vertical = 32.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(InfoIconBg),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .border(2.dp, InfoIcon, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "i", color = InfoIcon, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(20.dp))
        Column {
            Text(text = "출발 전 확인", color = PleosTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Text(text = "짐이 움직이지 않도록 잘 고정했는지 확인하세요", color = PleosTextSecondary, fontSize = 18.sp)
        }
    }
}

/** 적재 단계 카드: ① 제목 / 설명, 구분선 */
@Composable
private fun GuideStepsCard(steps: List<GuideStepUi>?) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, CardBorder, shape)
            .padding(horizontal = 32.dp, vertical = 16.dp),
    ) {
        if (steps == null) {
            // 안내를 아직 받는 중
            Row(
                modifier = Modifier.padding(vertical = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = RadioBlue, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(text = "가이드를 만들고 있어요", color = PleosTextSecondary, fontSize = 18.sp)
            }
            return@Column
        }
        steps.forEachIndexed { index, step ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CardBorder),
                )
            }
            Row(modifier = Modifier.padding(vertical = 24.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(StepBadgeBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "${step.number}", color = StepBadgeText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(text = step.title, color = PleosTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    step.body?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(text = it, color = PleosTextSecondary, fontSize = 17.sp, lineHeight = 24.sp)
                    }
                }
            }
        }
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
        val chosen = (state as? LoadResultState.Result)?.selected
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "배치 결과", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            chosen?.let { option ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(BadgeBg)
                        .padding(horizontal = 28.dp, vertical = 10.dp),
                ) {
                    Text(text = "총 ${option.passengerCount}명", color = BadgeText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
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
            val result = state as? LoadResultState.Result
            // 적재 불가능이면 고르기 전에도 "실을 수 있는 짐 + 못 싣는 짐"(첫 짐 빼기 대안)을 보여 준다 (시안)
            val shown = result?.selected ?: result?.takeIf { !it.possible }?.options?.firstOrNull { it.excludedNames.isNotEmpty() }
            TopView(
                layout = shown?.layout,
                dimmed = shown == null,
                showArrows = result?.possible == true,
                failedNames = shown?.excludedNames.orEmpty(),
            )
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
                    text = if (state.timedOut) "제한 시간 안에\n배치안을 찾지 못했어요" else "적재 가능한 배치를\n찾지 못했어요",
                    color = Color.White,
                    fontSize = 28.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
                is LoadResultState.Result -> if (shown == null && state.options.isNotEmpty()) {
                    Text(
                        text = "추천 방법을 선택하면\n배치를 보여드려요",
                        color = Color.White,
                        fontSize = 28.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                } else if (state.options.isEmpty()) {
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

/**
 * 투싼 탑뷰. 배치안이 있으면 짐을 청록 점선 칸으로 그린다 (시안: 좌석 표시칸 없이 짐만).
 * 트렁크에 싣는 짐은 아래(트렁크 입구)에서 넣는 방향 ↑를 붙이고, 그림 아래에 "트렁크 입구".
 */
@Composable
private fun TopView(
    layout: ArrangementLayoutUi?,
    dimmed: Boolean,
    showArrows: Boolean = true,
    /** 싣지 못하는 짐 (빨간 안내). 서버가 위치를 주지 않아 칸 대신 실린 짐 위에 경고 한 줄로 */
    failedNames: List<String> = emptyList(),
) {
    val image = ImageBitmap.imageResource(R.drawable.img_vehicle_tucson_cargo_view)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
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
            layout.items.forEach { item -> OverlayBox(item.rect, scale, ItemFill, item.name) }
            if (failedNames.isNotEmpty()) {
                // 실린 짐들 바로 위 (시안: "! 2열 좌석과 겹침")
                val top = layout.items.minOfOrNull { it.rect.top } ?: VehicleTopView.TRUNK.top
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = scale * (top - VehicleTopView.CROP_TOP) - 52.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(ImpossibleRed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "!", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${failedNames.joinToString(", ")} 불가능 · 공간 부족",
                        color = AlertRed,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            if (showArrows) layout.items.filter { it.spaceKind == ArrangementLayoutMapper.KIND_TRUNK }.forEach { item ->
                Text(
                    text = "↑",
                    color = ArrowTeal,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(
                        x = scale * ((item.rect.left + item.rect.right) / 2 - VehicleTopView.CROP_LEFT) - 10.dp,
                        y = scale * (VehicleTopView.TRUNK.bottom - VehicleTopView.CROP_TOP) - 6.dp,
                    ),
                )
            }
        }
        if (!dimmed) {
            Spacer(Modifier.height(8.dp))
            Text(text = "트렁크 입구", color = Color.White.copy(alpha = 0.6f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OverlayBox(rect: PxRect, scale: Dp, fill: Color, label: String, stroke: Color = ItemStroke) {
    Box(
        modifier = Modifier
            .offset(x = scale * (rect.left - VehicleTopView.CROP_LEFT), y = scale * (rect.top - VehicleTopView.CROP_TOP))
            .size((scale * rect.width).coerceAtLeast(20.dp), (scale * rect.height).coerceAtLeast(20.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(fill)
            .drawBehind {
                val inset = 1.dp.toPx()
                drawRoundRect(
                    color = stroke,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

private val CountBlue = Color(0xFF2E6BD6)
private val PossibleGreen = Color(0xFF2E9E86)
private val ImpossibleRed = Color(0xFFE02424)
private val AlertRed = Color(0xFFFF3B47)
private val CardBorder = Color(0xFFE4EAF3)
private val SelectedBorder = Color(0xFF3B82F6)
private val SelectedBg = Color(0xFFEFF5FF)
private val RadioBlue = Color(0xFF2563EB)
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
/** 배치한 짐 (시안: 짙은 청록 + 밝은 청록 점선) */
private val ItemFill = Color(0xCC14544F)
private val ItemStroke = Color(0xFF45D3C7)
private val ArrowTeal = Color(0xFF45D3C7)
private val BadgeBg = Color(0xFFDDF5EC)
private val BadgeText = Color(0xFF1F8A6E)
private val StepBadgeBg = Color(0xFFE6EEFB)
private val InfoTop = Color(0xFFF1F6FE)
private val InfoBottom = Color(0xFFE2ECFA)
private val InfoBorder = Color(0xFFCFDDF2)
private val InfoIconBg = Color(0xFFDCE7F8)
private val InfoIcon = Color(0xFF2E5FA8)
private val StepBadgeText = Color(0xFF2E5FA8)
