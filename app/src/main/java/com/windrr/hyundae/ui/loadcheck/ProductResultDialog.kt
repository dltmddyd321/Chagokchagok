package com.windrr.hyundae.ui.loadcheck

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.windrr.hyundae.R
import com.windrr.hyundae.ui.home.PleosNavy
import com.windrr.hyundae.ui.home.PleosTextPrimary
import com.windrr.hyundae.ui.home.PleosTextSecondary

/**
 * 찾은 제품 규격 (시안).
 * 왼쪽: 브랜드 칩 · 제품명 · 변형/모델 · 치수(가로×세로×높이) · 운반 상태, 오른쪽: 제품 이미지.
 * 서버는 제품 이미지를 주지 않아서 기본 짐 이미지를 쓴다.
 * 치수가 다 있으면 "이 짐 추가하기", 빠졌으면 "치수 입력하기"(아는 값만 채워 직접 입력 창).
 */
@Composable
fun ProductResultDialog(
    search: ProductSearchState,
    canAddItem: Boolean,
    onIntent: (LoadCheckIntent) -> Unit,
    onSpeakAgain: () -> Unit,
) {
    Dialog(
        onDismissRequest = { onIntent(LoadCheckIntent.CloseProductSearch) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 1000.dp)
                .fillMaxWidth(0.66f)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White)
                .padding(horizontal = 44.dp, vertical = 40.dp),
        ) {
            Text(text = "찾은 제품 규격", color = PleosTextPrimary, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(28.dp))
            when (search) {
                is ProductSearchState.Found -> FoundContent(search, onIntent)
                is ProductSearchState.NotFound -> NotFoundContent(search)
                is ProductSearchState.Searching -> Unit
            }
            Spacer(Modifier.height(24.dp))
            InfoBox(
                if (search is ProductSearchState.Found) {
                    "접거나 펼치면 크기가 달라질 수 있습니다.  실제로 실을 때의 크기를 확인해 주세요."
                } else {
                    "치수를 직접 입력하면 바로 짐으로 추가할 수 있어요."
                },
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ResultButton(text = "다시 말하기", primary = false, enabled = true, modifier = Modifier.weight(1f), onClick = onSpeakAgain)
                val ready = (search as? ProductSearchState.Found)?.selected?.hasAllDimensions == true
                if (ready) {
                    ResultButton(text = "+  이 짐 추가하기", primary = true, enabled = canAddItem, modifier = Modifier.weight(1f)) {
                        onIntent(LoadCheckIntent.AddProductCandidate)
                    }
                } else {
                    ResultButton(text = "치수 입력하기", primary = true, enabled = canAddItem, modifier = Modifier.weight(1f)) {
                        onIntent(LoadCheckIntent.EnterProductDimensions)
                    }
                }
            }
        }
    }
}

@Composable
private fun FoundContent(found: ProductSearchState.Found, onIntent: (LoadCheckIntent) -> Unit) {
    val candidate = found.selected
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(BrandChipBg)
                    .padding(horizontal = 28.dp, vertical = 10.dp),
            ) {
                Text(text = candidate.brand, color = BrandChipText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = candidate.name,
                color = PleosTextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            candidate.detail?.let {
                Spacer(Modifier.height(10.dp))
                Text(text = it, color = PleosTextSecondary, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(24.dp))
            Text(text = candidate.sizeLabel, color = SizeBlue, fontSize = 50.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "가로 × 세로 × 높이 · ${candidate.transportState.label}",
                color = PleosTextSecondary,
                fontSize = 18.sp,
            )
            if (!candidate.hasAllDimensions) {
                Spacer(Modifier.height(6.dp))
                Text(text = "확인되지 않은 치수(?)는 직접 입력해 주세요", color = WarnText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            candidate.sourceTitle?.let {
                Spacer(Modifier.height(6.dp))
                Text(text = "출처: $it", color = PleosTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // 같은 제품이 운반 상태별로 여러 개 오면 골라 보기
            if (found.candidates.size > 1) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    found.candidates.forEachIndexed { index, item ->
                        StateChip(
                            text = item.transportState.label,
                            selected = index == found.selectedIndex,
                        ) { onIntent(LoadCheckIntent.SelectProductCandidate(index)) }
                    }
                }
            }
        }
        Spacer(Modifier.width(32.dp))
        ProductImageBox(modifier = Modifier.weight(0.9f))
    }
}

@Composable
private fun NotFoundContent(search: ProductSearchState.NotFound) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "“${search.query}”",
                color = PleosTextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Text(text = search.message, color = PleosTextSecondary, fontSize = 18.sp, lineHeight = 26.sp)
        }
        Spacer(Modifier.width(32.dp))
        ProductImageBox(modifier = Modifier.weight(0.9f))
    }
}

/** 제품 이미지 자리: 서버가 이미지를 주지 않아 기본 짐 이미지 */
@Composable
private fun ProductImageBox(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .height(320.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, ImageBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.img_luggage_placeholder),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(150.dp),
        )
    }
}

@Composable
private fun InfoBox(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(InfoBg)
            .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .border(2.dp, InfoIcon, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "!", color = InfoIcon, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(16.dp))
        Text(text = text, color = PleosTextSecondary, fontSize = 18.sp)
    }
}

@Composable
private fun StateChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) PleosNavy else Color.White)
            .border(1.dp, if (selected) PleosNavy else ImageBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = text, color = if (selected) Color.White else PleosTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ResultButton(text: String, primary: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .height(84.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(4.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .background(
                if (primary) Brush.verticalGradient(listOf(PrimaryTop, PrimaryBottom)) else Brush.verticalGradient(listOf(Color.White, SecondaryBottom)),
            )
            .border(1.dp, if (primary) Color.Transparent else SecondaryBorder, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (primary) Color.White else PleosNavy,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
    }
}

private val BrandChipBg = Color(0xFFFBF1C7)
private val BrandChipText = Color(0xFFB8900A)
private val SizeBlue = Color(0xFF1E4C8C)
private val WarnText = Color(0xFFC0562A)
private val ImageBorder = Color(0xFFE2E8F2)
private val InfoBg = Color(0xFFEFF3FB)
private val InfoIcon = Color(0xFF2E5FA8)
private val PrimaryTop = Color(0xFF24477A)
private val PrimaryBottom = Color(0xFF14305A)
private val SecondaryBottom = Color(0xFFF0F5FC)
private val SecondaryBorder = Color(0xFFD6E1F0)
private val ShadowColor = Color(0x331B2B4B)

@Preview(widthDp = 1280, heightDp = 800, showBackground = true, backgroundColor = 0xFF3A4A5E)
@Composable
private fun FoundPreview() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.66f)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White)
                .padding(44.dp),
        ) {
            FoundContent(
                ProductSearchState.Found(
                    query = "이케아 락캅텐 아딜스 책상",
                    candidates = listOf(
                        ProductCandidateUi(
                            "c1", "IKEA", "락캅텐 / 아딜스 책상", "그레이/목재효과 블랙 · 895.840.27",
                            TransportState.ASSEMBLED, 120.0, 60.0, 73.0, "IKEA 제품 페이지",
                        ),
                        ProductCandidateUi("c2", "IKEA", "락캅텐 / 아딜스 책상", null, TransportState.PACKAGED, 121.0, 62.0, null, null),
                    ),
                ),
                onIntent = {},
            )
        }
    }
}
