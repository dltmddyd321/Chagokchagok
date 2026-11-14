package com.windrr.hyundae.ui.loadcheck

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.windrr.hyundae.R
import com.windrr.hyundae.ui.home.PleosNavy
import com.windrr.hyundae.ui.home.PleosTextPrimary
import com.windrr.hyundae.ui.home.PleosTextSecondary
import com.windrr.hyundae.ui.voice.ProductVoiceDialog

/**
 * 적재 확인 > 짐과 탑승 인원.
 * 짐(음성으로 찾기 / 치수 직접 입력, 최대 10개)과 동승자 수를 정하고 적재 가능 여부 확인으로 넘어간다.
 */
@Composable
fun LoadCheckRoute(
    onBack: () -> Unit,
    onRequestCheck: (items: List<LoadItem>, passengerCount: Int) -> Unit,
    viewModel: LoadCheckViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 제품명 말하기 다이얼로그 (열 때마다 새 키 → 새 음성 세션)
    var voiceSession by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LoadCheckEffect.RequestCheck -> onRequestCheck(effect.items, effect.passengerCount)
            }
        }
    }
    BackHandler(onBack = onBack)

    LoadCheckScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onVoiceSearch = { voiceSession = System.currentTimeMillis() },
    )
    // 검색 결과가 나오면 제품명 말하기 창을 닫고 "찾은 제품 규격" 창으로
    val searchDone = state.productSearch is ProductSearchState.Found || state.productSearch is ProductSearchState.NotFound
    LaunchedEffect(searchDone) { if (searchDone) voiceSession = null }
    voiceSession?.let { session ->
        ProductVoiceDialog(
            onDismiss = {
                voiceSession = null
                viewModel.onIntent(LoadCheckIntent.CloseProductSearch)
            },
            // 말이 끝나면 제품 규격 검색. 검색하는 동안 이 창은 "제품 규격을 찾는 중..."으로 남아 있다
            onRecognized = { text -> viewModel.onIntent(LoadCheckIntent.SearchProduct(text)) },
            viewModel = viewModel(key = "product-voice-$session"),
        )
    }
    if (searchDone) {
        state.productSearch?.let { search ->
            ProductResultDialog(
                search = search,
                canAddItem = state.canAddItem,
                onIntent = viewModel::onIntent,
                onSpeakAgain = {
                    viewModel.onIntent(LoadCheckIntent.CloseProductSearch)
                    voiceSession = System.currentTimeMillis()
                },
            )
        }
    }
    state.manualInput?.let { input -> ManualInputDialog(input = input, onIntent = viewModel::onIntent) }
}

@Composable
fun LoadCheckScreen(
    state: LoadCheckUiState,
    onIntent: (LoadCheckIntent) -> Unit,
    onVoiceSearch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 24.dp),
    ) {
        Text(text = "적재 확인  /  짐과 탑승 인원", color = PleosTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text(text = "짐과 탑승 인원을 확인해 주세요", color = PleosTextPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text(text = "가져갈 짐과 탑승 인원을 기준으로 적재 배치를 계산합니다", color = PleosTextSecondary, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 왼쪽: 음성 찾기 + 치수 직접 입력
            Column(
                modifier = Modifier
                    .weight(0.36f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                VoiceSearchCard(
                    enabled = state.canAddItem,
                    onClick = onVoiceSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                ManualInputCard(
                    enabled = state.canAddItem,
                    onClick = { onIntent(LoadCheckIntent.OpenManualInput) },
                )
            }
            // 오른쪽: 가져갈 짐 + 탑승 인원 + 확인
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ItemsCard(
                    items = state.items,
                    count = state.itemCount,
                    onRemove = { onIntent(LoadCheckIntent.RemoveItem(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                PassengerCard(
                    companions = state.companions,
                    total = state.totalPassengers,
                    onDecrease = { onIntent(LoadCheckIntent.DecreaseCompanions) },
                    onIncrease = { onIntent(LoadCheckIntent.IncreaseCompanions) },
                )
                CheckButton(enabled = state.canCheck, onClick = { onIntent(LoadCheckIntent.Check) })
            }
        }
    }
}

/** 남색 카드: 마이크 원형 버튼 + 제품명 말하기 + 음성으로 제품 사이즈 찾기 */
@Composable
private fun VoiceSearchCard(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .shadow(10.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(VoiceTop, VoiceBottom)))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(MicTop, MicBottom))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_mic),
                    contentDescription = "제품명 말하기",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(text = "제품명 말하기", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text(text = "“이케아 락캅텐 아딜스 책상”", color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "음성으로 제품 사이즈 찾기", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 밝은 카드: ⌨ 치수 직접 입력 > */
@Composable
private fun ManualInputCard(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .shadow(6.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color.White, LightCardTint)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(R.drawable.ic_keyboard), contentDescription = null, tint = PleosNavy, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = "치수 직접 입력",
            color = PleosTextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = PleosTextSecondary)
    }
}

/** 가져갈 짐 N + 목록 (없으면 점선 빈 칸) */
@Composable
private fun ItemsCard(items: List<LoadItem>, count: Int, onRemove: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.6f))
            .border(1.dp, Color.White, shape)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "가져갈 짐 ", color = PleosTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = "$count", color = if (count > 0) PleosNavy else CountZero, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text(text = "최대 ${LoadCheckUiState.MAX_ITEMS}개까지 추가할 수 있습니다", color = PleosTextSecondary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(18.dp))
        if (items.isEmpty()) {
            EmptyItems(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(items, key = { it.key }) { item -> ItemTile(item = item, onRemove = { onRemove(item.key) }) }
            }
        }
    }
}

@Composable
private fun EmptyItems(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                color = DashColor,
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
            )
        }
        Text(text = "추가한 짐이 여기에 표시됩니다", color = PleosTextSecondary, fontSize = 16.sp)
    }
}

/** 짐 한 칸: 이미지 · 이름 · 크기 · 수량 · ✕ */
@Composable
private fun ItemTile(item: LoadItem, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 임시 이미지: 제품 이미지가 없으면 캐리어 사진
        Image(
            painter = painterResource(R.drawable.img_item_suitcase_travel_bag),
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LightCardTint),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, color = PleosTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(text = "${item.sizeLabel} · ${item.quantity}개", color = PleosTextSecondary, fontSize = 13.sp)
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(RemoveBg)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "✕", color = PleosTextSecondary, fontSize = 13.sp)
        }
    }
}

/** 탑승인원 · 운전자 1명 포함 | 동승자 [-] N [+] | 총 N명 탑승 */
@Composable
private fun PassengerCard(companions: Int, total: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .shadow(4.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .background(Color.White)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "탑승인원", color = PleosTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(16.dp))
        Text(text = "운전자 1명 포함", color = PleosTextSecondary, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Text(text = "동승자", color = PleosTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(24.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(StepperBg)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(text = "−", enabled = companions > 0, onClick = onDecrease)
            Text(
                text = "$companions",
                color = PleosTextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp),
            )
            StepButton(text = "+", enabled = companions < LoadCheckUiState.MAX_COMPANIONS, onClick = onIncrease)
        }
        Spacer(Modifier.width(28.dp))
        Text(text = "총 ${total}명 탑승", color = PleosNavy, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit, size: Dp = 48.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f)
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(StepTop, StepBottom)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = PleosNavy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/** 적재 가능 여부 확인 (짐이 있어야 활성) */
@Composable
private fun CheckButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(shape)
            .background(
                if (enabled) Brush.verticalGradient(listOf(EnabledTop, EnabledBottom)) else Brush.verticalGradient(listOf(DisabledTop, DisabledBottom)),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "적재 가능 여부 확인",
            color = if (enabled) Color.White else DisabledText,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/**
 * 짐 치수 직접 입력 (시안): 왼쪽 가로·세로·높이(−/값/+), 오른쪽 숫자판, 아래 취소/저장.
 * 항목을 누르면 선택되고, 숫자판 입력은 선택한 항목에 들어간다.
 */
@Composable
private fun ManualInputDialog(input: ManualInputState, onIntent: (LoadCheckIntent) -> Unit) {
    Dialog(
        onDismissRequest = { onIntent(LoadCheckIntent.CloseManualInput) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White)
                .padding(horizontal = 44.dp, vertical = 40.dp),
        ) {
            Text(text = "짐 치수 직접 입력", color = PleosTextPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Text(text = "치수 항목을 선택한 뒤 숫자판으로 입력해 주세요", color = PleosTextSecondary, fontSize = 17.sp)
            if (input.name.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = "“${input.name}”", color = PleosNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ManualField.entries.forEach { field ->
                        DimensionRow(
                            field = field,
                            value = input.valueOf(field),
                            selected = input.selected == field,
                            onIntent = onIntent,
                        )
                    }
                }
                Keypad(onIntent = onIntent, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                DialogButton("취소", primary = false, enabled = true, modifier = Modifier.weight(1f)) {
                    onIntent(LoadCheckIntent.CloseManualInput)
                }
                DialogButton("저장", primary = true, enabled = input.canSave, modifier = Modifier.weight(1f)) {
                    onIntent(LoadCheckIntent.ConfirmManualInput)
                }
            }
        }
    }
}

/** 가로 (cm)  [−]  0  [+] — 선택하면 파란 테두리·연한 파랑 */
@Composable
private fun DimensionRow(field: ManualField, value: String, selected: Boolean, onIntent: (LoadCheckIntent) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(shape)
            .background(if (selected) RowSelectedBg else RowBg)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) RowSelectedBorder else RowBorder, shape)
            .clickable { onIntent(LoadCheckIntent.SelectField(field)) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${field.label} (cm)",
            color = PleosTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        KeyButton(text = "−", width = 58.dp, height = 56.dp, tinted = false) { onIntent(LoadCheckIntent.StepField(field, -1)) }
        Text(
            text = value.ifEmpty { "0" },
            color = PleosTextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp),
        )
        KeyButton(text = "+", width = 58.dp, height = 56.dp, tinted = true) { onIntent(LoadCheckIntent.StepField(field, 1)) }
    }
}

/** 숫자판: 1~9, 지우기, 0, ⌫ */
@Composable
private fun Keypad(onIntent: (LoadCheckIntent) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(KeypadBg)
            .border(1.dp, RowBorder, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "숫자판", color = PleosTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { d ->
                    KeyButton(text = "$d", modifier = Modifier.weight(1f)) { onIntent(LoadCheckIntent.KeypadDigit(d)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            KeyButton(text = "지우기", tinted = true, fontSize = 18.sp, modifier = Modifier.weight(1f)) { onIntent(LoadCheckIntent.KeypadClear) }
            KeyButton(text = "0", modifier = Modifier.weight(1f)) { onIntent(LoadCheckIntent.KeypadDigit(0)) }
            KeyButton(text = "⌫", tinted = true, modifier = Modifier.weight(1f)) { onIntent(LoadCheckIntent.KeypadBackspace) }
        }
    }
}

/** 숫자판·−/+ 버튼: 흰(또는 연한 파랑) 그라데이션 + 살짝 그림자 */
@Composable
private fun KeyButton(
    text: String,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 64.dp,
    tinted: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 28.sp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .shadow(2.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .background(Brush.verticalGradient(if (tinted) listOf(StepTop, StepBottom) else listOf(Color.White, KeyBottom)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = if (tinted) PleosNavy else PleosTextPrimary, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DialogButton(text: String, primary: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(76.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(4.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .background(
                if (primary) Brush.verticalGradient(listOf(EnabledTop, EnabledBottom)) else Brush.verticalGradient(listOf(Color.White, KeyBottom)),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = if (primary) Color.White else PleosNavy, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private val RowBg = Color(0xFFF6F8FC)
private val RowBorder = Color(0xFFE2E8F2)
private val RowSelectedBg = Color(0xFFEAF2FE)
private val RowSelectedBorder = Color(0xFF6F9CE0)
private val KeypadBg = Color(0xFFF1F5FB)
private val KeyBottom = Color(0xFFF2F5FA)
private val ShadowColor = Color(0x331B2B4B)
private val VoiceTop = Color(0xFF2A4A72)
private val VoiceBottom = Color(0xFF1A3352)
private val MicTop = Color(0xFF1F3F6A)
private val MicBottom = Color(0xFF12294A)
private val LightCardTint = Color(0xFFE2EAF7)
private val CountZero = Color(0xFFB9C3D3)
private val DashColor = Color(0xFFB7C6DD)
private val RemoveBg = Color(0xFFF0F2F6)
private val StepperBg = Color(0xFFEFF3F9)
private val StepTop = Color(0xFFF2F6FC)
private val StepBottom = Color(0xFFDCE5F2)
private val EnabledTop = Color(0xFF24477A)
private val EnabledBottom = Color(0xFF14305A)
private val DisabledTop = Color(0xFFE8EDF4)
private val DisabledBottom = Color(0xFFDDE3EC)
private val DisabledText = Color(0xFF7D879A)

@Preview(widthDp = 1280, heightDp = 800, showBackground = true, backgroundColor = 0xFFEFF2F8)
@Composable
private fun LoadCheckScreenPreview() {
    LoadCheckScreen(state = LoadCheckUiState(companions = 2), onIntent = {}, onVoiceSearch = {})
}
