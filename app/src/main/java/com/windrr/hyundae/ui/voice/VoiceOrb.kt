package com.windrr.hyundae.ui.voice

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * 음성에 반응하는 네이비·블루 구체.
 * https://navy-voice-orb-1113.kdj0689.workers.dev 의 캔버스 그리기를 그대로 옮겼다
 * (그라데이션 색·위치·세기, 윤곽 흔들림, 반응 속도 모두 같은 값).
 *
 * @param level 지금 소리 크기 0~1 (음성 인식 RMS를 정규화한 값)
 * @param active 듣는 중이면 true — 빛이 살아나고, 끝나면 잔잔해진다
 */
@Composable
fun VoiceOrb(level: Float, active: Boolean, modifier: Modifier = Modifier) {
    val target by rememberUpdatedState(level.coerceIn(0f, 1f))
    val isActive by rememberUpdatedState(active)
    var smoothLevel by remember { mutableFloatStateOf(0f) }
    var energy by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                val t = if (isActive) target else 0f
                // 빠르게 커지고(60ms) 천천히 줄어든다(240ms) — 프레임 수와 무관
                smoothLevel += (t - smoothLevel) * (1f - exp(-dt / if (t > smoothLevel) 0.06f else 0.24f))
                val lightTarget = if (isActive) (0.18f + smoothLevel * 0.9f).coerceIn(0f, 1f) else 0f
                energy += (lightTarget - energy) * (1f - exp(-dt / 0.35f))
                // 소리가 없어도 천천히 흐르고(물결), 말소리가 커지면 빨라진다
                phase += dt * (IDLE_SPEED + smoothLevel * 2.1f)
            }
        }
    }

    Canvas(modifier) { drawOrb(smoothLevel, energy, phase) }
}

private fun DrawScope.drawOrb(level: Float, energy: Float, phase: Float) {
    val size = min(size.width, size.height)
    val cx = this.size.width / 2f
    val cy = this.size.height / 2f
    // 숨 쉬듯 아주 살짝 커졌다 작아짐 + 소리 크기만큼 커짐
    val breathe = 1 + sin(phase * 0.9f) * 0.012f
    val r = size * 0.355f * (1 + level * 0.06f) * breathe
    // 윤곽 물결: 조용할 때도 IDLE_WAVE만큼 일렁인다
    val path = orbPath(cx, cy, r, IDLE_WAVE + level * 1.25f, phase)

    // 바깥 은은한 빛 (반지름 r*0.84 ~ r*1.19 사이에만)
    val haloR = r * 1.19f
    val inner = 0.84f / 1.19f
    drawRect(
        Brush.radialGradient(
            0f to Color(31, 103, 239, 0),
            inner to Color(31, 103, 239, 0),
            (inner + (1 - inner) * 0.44f) to Color(35, 113, 255, ((0.025f + energy * 0.08f) * 255).toInt()),
            1f to Color(16, 50, 100, 0),
            center = Offset(cx, cy),
            radius = haloR,
        ),
    )

    clipPath(path) {
        drawRect(
            Brush.linearGradient(
                0f to css("#07132f"),
                0.38f to css("#09235e"),
                0.7f to css("#1145b7"),
                1f to css("#317fe8"),
                start = Offset(cx - r, cy - r),
                end = Offset(cx + r, cy + r),
            ),
        )
        // 안쪽 빛이 천천히 흘러 다닌다 (조용할 때도)
        val drift = sin(phase * 0.65f) * (IDLE_DRIFT + level * 0.13f)
        val sway = cos(phase * 0.47f) * IDLE_DRIFT
        light(cx + r * (0.40f + sway), cy + r * (0.30f - drift), r * 1.5f, listOf(0f to "#0d51ed", 0.5f to "#0a40d0", 1f to "#081b4600"), 0.66f + energy * 0.34f)
        light(cx - r * 0.52f, cy - r * 0.15f, r * 1.12f, listOf(0f to "#01071f", 0.5f to "#030c30ed", 1f to "#05123600"), 1f)
        light(cx + r * (0.96f + drift), cy - r * 0.05f, r * 0.85f, listOf(0f to "#b0e7ff", 0.18f to "#63c6ff", 0.5f to "#278cffbb", 1f to "#1467d700"), 0.50f + energy * 0.36f)
        light(cx + r * 0.04f, cy - r * (1.02f - drift), r * 0.88f, listOf(0f to "#6fe1ff", 0.25f to "#25a6fbd9", 0.65f to "#147ee34d", 1f to "#145fbb00"), 0.54f + energy * 0.33f)
        light(cx - r * (0.24f - sway), cy + r * 1.05f, r * 0.76f, listOf(0f to "#27d5ff", 0.22f to "#0ca5fa", 0.60f to "#1558ed72", 1f to "#1642e500"), 0.61f + energy * 0.35f)
        light(cx - r * 0.55f, cy + r * 0.34f, r * 0.63f, listOf(0f to "#1122bd82", 1f to "#131eb900"), 0.7f)
        // 안쪽으로 파인 듯한 네이비 음영 (흰 광택 없이 깊이감)
        light(cx - r * 0.28f + drift * r, cy - r * 0.09f, r * 0.85f, listOf(0f to "#020b27a6", 0.55f to "#071f4930", 1f to "#07244b00"), 0.6f)
        if (energy < 0.65f) drawRect(Color(5, 16, 39, (0.15f * (1 - energy) * 255).toInt()))
    }

    drawPath(
        path,
        Brush.linearGradient(
            0f to css("#2637cc50"),
            0.35f to css("#315ed070"),
            0.72f to css("#4baafca0"),
            1f to css("#a2e4ffb8"),
            start = Offset(cx - r, cy),
            end = Offset(cx + r, cy - r * 0.4f),
        ),
        style = Stroke(width = (1f + energy * 0.6f) * density),
    )
}

private const val IDLE_SPEED = 0.9f
private const val IDLE_WAVE = 0.55f
private const val IDLE_DRIFT = 0.06f

/** 원 둘레를 소리 크기만큼 부드럽게 흔든다 (주기 함수라 이음새 없음) */
private fun orbPath(cx: Float, cy: Float, r: Float, amount: Float, phase: Float): Path = Path().apply {
    val points = 180
    for (i in 0..points) {
        val theta = i.toFloat() / points * 2f * PI.toFloat()
        val wave = sin(theta * 3 + phase) * 0.015f + sin(theta * 5 - phase * 0.8f) * 0.009f + cos(theta * 2 - phase * 0.55f) * 0.011f
        val radius = r * (1 + wave * amount)
        val x = cx + cos(theta) * radius
        val y = cy + sin(theta) * radius
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.light(x: Float, y: Float, radius: Float, stops: List<Pair<Float, String>>, alpha: Float) {
    drawRect(
        Brush.radialGradient(*stops.map { (at, hex) -> at to css(hex) }.toTypedArray(), center = Offset(x, y), radius = radius),
        alpha = alpha.coerceIn(0f, 1f),
    )
}

/** CSS 색 "#rrggbb" / "#rrggbbaa" → Color */
private fun css(hex: String): Color {
    val h = hex.removePrefix("#")
    val r = h.substring(0, 2).toInt(16)
    val g = h.substring(2, 4).toInt(16)
    val b = h.substring(4, 6).toInt(16)
    val a = if (h.length == 8) h.substring(6, 8).toInt(16) else 255
    return Color(r, g, b, a)
}
