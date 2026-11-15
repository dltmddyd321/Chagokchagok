package com.windrr.hyundae.ui.quick

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.windrr.hyundae.ui.loadcheck.ProductCandidateUi
import com.windrr.hyundae.ui.voice.ProductVoiceEffect
import com.windrr.hyundae.ui.voice.ProductVoiceIntent
import com.windrr.hyundae.ui.voice.ProductVoiceViewModel
import com.windrr.hyundae.ui.voice.VoicePhase
import com.windrr.hyundae.ui.voice.VoiceOrb

/**
 * 빠른 확인 (왼쪽 메뉴): 제품명을 말하면 규격을 찾아 지금 차에 들어가는지 바로 알려 준다 (시안 3장).
 * 듣는 중 = 어두운 화면 + 음성 orb, 결과 = 밝은 화면 + 배치 가능/불가 카드.
 */
@Composable
fun QuickCheckRoute(
    onExit: () -> Unit,
    viewModel: QuickCheckViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 들을 때마다 새 음성 세션 (새 ViewModel). 탭에 다시 들어와도 새로 듣는다
    var session by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val listenAgain = {
        session = System.currentTimeMillis()
        viewModel.listenAgain()
    }
    val exit = {
        viewModel.listenAgain()
        onExit()
    }

    when (val s = state) {
        QuickCheckState.Listening, is QuickCheckState.Searching -> ListeningScreen(
            searchingQuery = (s as? QuickCheckState.Searching)?.query,
            voiceViewModel = viewModel(key = "quick-voice-$session"),
            onRecognized = viewModel::onUtterance,
            onExit = exit,
        )
        is QuickCheckState.Result -> ResultScreen(
            fits = s.fits,
            product = s.product,
            hint = s.hint,
            onSpeakAgain = listenAgain,
            onExit = exit,
        )
        is QuickCheckState.Message -> MessageScreen(
            title = s.title,
            body = s.body,
            onSpeakAgain = listenAgain,
            onExit = exit,
        )
    }
}

/** 어두운 듣기 화면: "제품명을 말씀해 주세요" + orb + 음성 대화 끝내기 */
@Composable
private fun ListeningScreen(
    searchingQuery: String?,
    voiceViewModel: ProductVoiceViewModel,
    onRecognized: (String) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val voice by voiceViewModel.state.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voiceViewModel.onIntent(ProductVoiceIntent.Start) else onExit()
    }
    LaunchedEffect(voiceViewModel) {
        if (searchingQuery != null) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceViewModel.onIntent(ProductVoiceIntent.Start)
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // 디버그 빌드: 말한 것처럼 넣으면 실제 인식처럼 글자가 차례로 보인 뒤 인식 완료
    if (searchingQuery == null) com.windrr.hyundae.speech.DebugSpeechInput(onText = voiceViewModel::debugSpeak)
    LaunchedEffect(voiceViewModel) {
        voiceViewModel.effect.collect { effect ->
            when (effect) {
                is ProductVoiceEffect.Recognized -> onRecognized(effect.text)
                ProductVoiceEffect.Dismiss -> onExit()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 24.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(DarkTop, DarkBottom)))
            .padding(horizontal = 48.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.6f))
        Text(text = "제품명을 말씀해 주세요", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Text(
            text = "브랜드와 제품명을 함께 말하면 규격을 찾을 수 있습니다",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(0.4f))
        VoiceOrb(
            level = voice.level,
            active = searchingQuery == null && voice.phase == VoicePhase.LISTENING,
            modifier = Modifier.size(300.dp),
        )
        Spacer(Modifier.height(20.dp))
        val transcript = searchingQuery ?: voice.transcript
        Text(
            text = transcript.takeIf { it.isNotBlank() }?.let { "“$it”" } ?: " ",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (searchingQuery != null) "제품 규격을 찾는 중..." else voice.statusText,
            color = if (voice.phase == VoicePhase.ERROR && searchingQuery == null) ErrorText else StatusBlue,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(0.6f))
        WideButton(text = "음성 대화 끝내기", style = ButtonStyle.GLASS, modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(0.85f)) {
            voiceViewModel.onIntent(ProductVoiceIntent.Cancel)
            onExit()
        }
    }
}

/** 밝은 결과 화면: 내 차에 실을 수 있어요! / 없어요 ! */
@Composable
private fun ResultScreen(
    fits: Boolean,
    product: ProductCandidateUi,
    hint: String,
    onSpeakAgain: () -> Unit,
    onExit: () -> Unit,
) {
    LightPanel(onSpeakAgain = onSpeakAgain, onExit = onExit) {
        Text(
            text = if (fits) "내 차에 실을 수 있어요!" else "내 차에 실을 수 없어요 !",
            color = TitleNavy,
            fontSize = 44.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Text(text = "알려주신 제품을 기준으로 사이즈를 확인했어요", color = BodyGray, fontSize = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        Column(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .fillMaxWidth(0.8f)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(CardTop, CardBottom)))
                .padding(vertical = 36.dp, horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (fits) OkBadgeBg else NoBadgeBg)
                    .padding(horizontal = 40.dp, vertical = 10.dp),
            ) {
                Text(
                    text = if (fits) "배치 가능" else "배치 불가",
                    color = if (fits) OkBadgeText else NoBadgeText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = ProductCandidateUi.shortName(product.name),
                color = TitleNavy,
                fontSize = 32.sp,
                // 두 줄일 때 줄이 겹치지 않게 (좁은 화면에서 이름이 길면 두 줄)
                lineHeight = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            product.detail?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = it, color = BodyGray, fontSize = 19.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            // 좁은 화면(내비 분할)에서는 치수가 줄바꿈되지 않게 글자를 줄인다
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                Text(
                    text = product.sizeLabel,
                    color = SizeBlue,
                    fontSize = if (maxWidth < 560.dp) 38.sp else 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(text = hint.sentencePerLine(), color = BodyGray, fontSize = 19.sp, lineHeight = 28.sp, textAlign = TextAlign.Center)
    }
}

/** 제품을 못 찾았을 때 등 */
@Composable
private fun MessageScreen(title: String, body: String, onSpeakAgain: () -> Unit, onExit: () -> Unit) {
    LightPanel(onSpeakAgain = onSpeakAgain, onExit = onExit) {
        Text(text = title, color = TitleNavy, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Text(text = body, color = BodyGray, fontSize = 20.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LightPanel(onSpeakAgain: () -> Unit, onExit: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 24.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(LightTop, LightBottom)))
            .padding(horizontal = 48.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.5f))
        content()
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .widthIn(max = 1000.dp)
                .fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WideButton(text = "다시 말하기", style = ButtonStyle.WHITE, modifier = Modifier.weight(0.42f), onClick = onSpeakAgain)
            WideButton(text = "음성 대화 끝내기", style = ButtonStyle.NAVY, modifier = Modifier.weight(0.58f), onClick = onExit)
        }
    }
}

private enum class ButtonStyle { GLASS, WHITE, NAVY }

@Composable
private fun WideButton(text: String, style: ButtonStyle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .height(88.dp)
            .shadow(if (style == ButtonStyle.GLASS) 0.dp else 6.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .background(
                when (style) {
                    ButtonStyle.GLASS -> Brush.verticalGradient(listOf(GlassTop, GlassBottom))
                    ButtonStyle.WHITE -> Brush.verticalGradient(listOf(Color.White, WhiteBottom))
                    ButtonStyle.NAVY -> Brush.verticalGradient(listOf(NavyTop, NavyBottom))
                },
            )
            .border(1.dp, if (style == ButtonStyle.GLASS) GlassBorder else Color.Transparent, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (style == ButtonStyle.WHITE) TitleNavy else Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

private val DarkTop = Color(0xFF0F1823)
private val DarkBottom = Color(0xFF07182A)
private val StatusBlue = Color(0xFF8DB8FF)
private val ErrorText = Color(0xFFFF9C9C)
private val GlassTop = Color(0xFF23405E)
private val GlassBottom = Color(0xFF16304D)
private val GlassBorder = Color(0xFF3A5A80)
private val LightTop = Color(0xFFAEBBD3)
private val LightBottom = Color(0xFFF1F4FA)
private val CardTop = Color(0xFFF4F8FE)
private val CardBottom = Color(0xFFE1ECFA)
private val TitleNavy = Color(0xFF15253F)
private val BodyGray = Color(0xFF55627A)
private val SizeBlue = Color(0xFF2559A6)
private val OkBadgeBg = Color(0xFFDDF2E5)
private val OkBadgeText = Color(0xFF1F7A4A)
private val NoBadgeBg = Color(0xFFE6DEDD)
private val NoBadgeText = Color(0xFF8A4A33)
private val WhiteBottom = Color(0xFFF2F5FA)
private val NavyTop = Color(0xFF24456E)
private val NavyBottom = Color(0xFF142C4B)
private val ShadowColor = Color(0x331B2B4B)

/** "A요. B세요." → 문장마다 줄바꿈 */
private fun String.sentencePerLine(): String = trim().replace(Regex("([.!?])\\s+"), "$1\n")
