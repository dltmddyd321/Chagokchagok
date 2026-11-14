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
    voiceSession?.let { session ->
        ProductVoiceDialog(
            onDismiss = { voiceSession = null },
            onRecognized = { text ->
                // TODO: 제품 규격 API(제품명 → 가로·세로·높이) 연결. 지금은 이름만 채워 치수 직접 입력 창으로
                voiceSession = null
                viewModel.onIntent(LoadCheckIntent.OpenManualInputWithName(text))
            },
            viewModel = viewModel(key = "product-voice-$session"),
        )
    }
    state.manualInput?.let { input ->
        ManualInputDialog(
            input = input,
            maxQuantity = (LoadCheckUiState.MAX_ITEMS - state.itemCount).coerceAtLeast(1),
            onIntent = viewModel::onIntent,
        )
    }
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

/** 치수 직접 입력 창: 이름 · 가로/세로/높이(cm) · 수량 */
@Composable
private fun ManualInputDialog(input: ManualInputState, maxQuantity: Int, onIntent: (LoadCheckIntent) -> Unit) {
    Dialog(onDismissRequest = { onIntent(LoadCheckIntent.CloseManualInput) }) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(28.dp),
        ) {
            Text(text = "치수 직접 입력", color = PleosTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(20.dp))
            DialogField("짐 이름", input.name, "예: 책상", KeyboardType.Text) { onIntent(LoadCheckIntent.ManualChanged(ManualField.NAME, it)) }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    DialogField("가로 (cm)", input.width, "0", KeyboardType.Number) { onIntent(LoadCheckIntent.ManualChanged(ManualField.WIDTH, it)) }
                }
                Box(Modifier.weight(1f)) {
                    DialogField("세로 (cm)", input.depth, "0", KeyboardType.Number) { onIntent(LoadCheckIntent.ManualChanged(ManualField.DEPTH, it)) }
                }
                Box(Modifier.weight(1f)) {
                    DialogField("높이 (cm)", input.height, "0", KeyboardType.Number) { onIntent(LoadCheckIntent.ManualChanged(ManualField.HEIGHT, it)) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "수량", color = PleosTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                StepButton(text = "−", enabled = input.quantity > 1, onClick = { onIntent(LoadCheckIntent.ManualQuantity(-1)) }, size = 44.dp)
                Text(
                    text = "${input.quantity}",
                    color = PleosTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(56.dp),
                )
                StepButton(text = "+", enabled = input.quantity < maxQuantity, onClick = { onIntent(LoadCheckIntent.ManualQuantity(1)) }, size = 44.dp)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton("취소", primary = false, enabled = true, modifier = Modifier.weight(1f)) {
                    onIntent(LoadCheckIntent.CloseManualInput)
                }
                DialogButton("추가", primary = true, enabled = input.canAdd, modifier = Modifier.weight(1f)) {
                    onIntent(LoadCheckIntent.ConfirmManualInput)
                }
            }
        }
    }
}

@Composable
private fun DialogField(label: String, value: String, placeholder: String, keyboardType: KeyboardType, onChange: (String) -> Unit) {
    Column {
        Text(text = label, color = PleosTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = PleosTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(PleosNavy),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FieldBg)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    if (value.isEmpty()) Text(text = placeholder, color = PlaceholderColor, fontSize = 18.sp)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun DialogButton(text: String, primary: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) PleosNavy else FieldBg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = if (primary) Color.White else PleosTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

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
private val FieldBg = Color(0xFFF1F4F9)
private val PlaceholderColor = Color(0xFFB2B9C6)

@Preview(widthDp = 1280, heightDp = 800, showBackground = true, backgroundColor = 0xFFEFF2F8)
@Composable
private fun LoadCheckScreenPreview() {
    LoadCheckScreen(state = LoadCheckUiState(companions = 2), onIntent = {}, onVoiceSearch = {})
}
