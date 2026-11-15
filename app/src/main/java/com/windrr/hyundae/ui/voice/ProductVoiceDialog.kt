package com.windrr.hyundae.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 제품명 말하기 다이얼로그.
 * 열리면 바로 듣기 시작 → orb가 목소리 크기에 반응 → 말이 끝나고 침묵이 이어지면 [onRecognized].
 */
@Composable
fun ProductVoiceDialog(
    onDismiss: () -> Unit,
    onRecognized: (String) -> Unit,
    viewModel: ProductVoiceViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onIntent(ProductVoiceIntent.Start) else onDismiss()
    }
    LaunchedEffect(viewModel) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.onIntent(ProductVoiceIntent.Start)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    com.windrr.hyundae.speech.DebugSpeechInput(onText = viewModel::debugSpeak)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ProductVoiceEffect.Recognized -> onRecognized(effect.text)
                ProductVoiceEffect.Dismiss -> onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = { viewModel.onIntent(ProductVoiceIntent.Cancel) },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        ProductVoiceContent(state = state, onIntent = viewModel::onIntent)
    }
}

@Composable
fun ProductVoiceContent(state: ProductVoiceUiState, onIntent: (ProductVoiceIntent) -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier = Modifier
            .widthIn(max = 860.dp)
            .fillMaxWidth(0.66f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(PanelTop, PanelBottom)))
            .border(1.dp, PanelBorder, shape)
            .padding(horizontal = 56.dp, vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "제품명을 말씀해 주세요", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.dp))
        Text(text = "브랜드와 제품명을 함께 말하면 규격을 찾을 수 있습니다", color = Color.White.copy(alpha = 0.75f), fontSize = 17.sp)
        Spacer(Modifier.height(12.dp))

        VoiceOrb(
            level = state.level,
            active = state.phase == VoicePhase.LISTENING,
            modifier = Modifier.size(260.dp),
        )

        Text(
            text = state.transcript.takeIf { it.isNotBlank() }?.let { "“$it”" } ?: "“이케아 락캅텐 아딜스 책상”",
            color = if (state.transcript.isNotBlank()) Color.White else Color.White.copy(alpha = 0.35f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = state.statusText,
            color = if (state.phase == VoicePhase.ERROR) ErrorText else StatusBlue,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(36.dp))

        Row(
            modifier = Modifier.fillMaxWidth(0.82f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.phase == VoicePhase.ERROR) {
                PanelButton(text = "다시 말하기", primary = true, modifier = Modifier.weight(1f)) { onIntent(ProductVoiceIntent.Retry) }
            }
            PanelButton(text = "취소", primary = false, modifier = Modifier.weight(1f)) { onIntent(ProductVoiceIntent.Cancel) }
        }
    }
}

@Composable
private fun PanelButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(68.dp)
            .clip(shape)
            .background(
                if (primary) Brush.verticalGradient(listOf(PrimaryTop, PrimaryBottom)) else Brush.verticalGradient(listOf(CancelTop, CancelBottom)),
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private val PanelTop = Color(0xFF111A24)
private val PanelBottom = Color(0xFF0A1726)
private val PanelBorder = Color(0xFF2E5E9A)
private val StatusBlue = Color(0xFF8DB8FF)
private val ErrorText = Color(0xFFFF9C9C)
private val CancelTop = Color(0xFF3A4A5E)
private val CancelBottom = Color(0xFF26323F)
private val PrimaryTop = Color(0xFF2E66D6)
private val PrimaryBottom = Color(0xFF1C4AA8)

@Preview(widthDp = 1280, heightDp = 800, showBackground = true, backgroundColor = 0xFF3A4A5E)
@Composable
private fun ProductVoiceContentPreview() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ProductVoiceContent(
            state = ProductVoiceUiState(phase = VoicePhase.LISTENING, transcript = "이케아 락캅텐 아딜스 책상", level = 0.5f),
            onIntent = {},
        )
    }
}
