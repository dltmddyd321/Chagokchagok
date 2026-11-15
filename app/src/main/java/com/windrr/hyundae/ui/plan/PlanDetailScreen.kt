package com.windrr.hyundae.ui.plan

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.windrr.hyundae.R
import com.windrr.hyundae.ui.home.PleosNavy
import com.windrr.hyundae.ui.home.PleosTextPrimary
import com.windrr.hyundae.ui.home.PleosTextSecondary
import com.windrr.hyundae.ui.loadcheck.ManualInputDialog

/**
 * 내 계획 / 상세정보 (시안): Pleos에 선택된 최신 계획.
 * 왼쪽: 차 사진 · 탑승 인원(동승자 −/+), 오른쪽: 가져갈 짐(수정 → 짐 치수 수정 창), 아래: 뒤로 가기 · 배치도 미리보기 · 계획 진행하기.
 */
@Composable
fun PlanDetailRoute(
    onBack: () -> Unit,
    viewModel: PlanDetailViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.dismissMessage()
        }
    }
    PlanDetailScreen(
        state = state,
        onBack = onBack,
        onCompanions = viewModel::changeCompanions,
        onEdit = viewModel::openEdit,
    )
    state.editing?.let { ManualInputDialog(input = it, onIntent = viewModel::onEditIntent) }
}

@Composable
fun PlanDetailScreen(
    state: PlanDetailUiState,
    onBack: () -> Unit,
    onCompanions: (Int) -> Unit,
    onEdit: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 24.dp),
    ) {
        Text(text = "내계획  /  상세정보", color = PleosTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (state.planId == null && !state.loading) "저장한 계획이 없어요" else state.name,
            color = PleosTextPrimary,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (state.planId == null && !state.loading) "모바일에서 계획을 저장하면 여기에서 확인할 수 있어요" else "가져갈 짐과 탑승 인원을 확인하고 수정할 수 있습니다",
            color = PleosTextSecondary,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 왼쪽: 차 사진 + 탑승 인원
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.img_banner_weekend_outing_lakeside),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = ShadowColor, spotColor = ShadowColor)
                        .clip(RoundedCornerShape(24.dp)),
                )
                PassengerCard(
                    total = state.totalPassengers,
                    companions = state.companions,
                    enabled = state.planId != null && !state.saving,
                    onCompanions = onCompanions,
                )
            }
            // 오른쪽: 가져갈 짐
            ItemsCard(
                state = state,
                onEdit = onEdit,
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(modifier = Modifier.weight(0.42f)) {
                BottomButton(text = "뒤로 가기", primary = false, modifier = Modifier.fillMaxWidth(0.45f), onClick = onBack)
            }
            Row(modifier = Modifier.weight(0.58f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // TODO: 배치도 미리보기·계획 진행하기 동작은 추후 (지금은 버튼만)
                BottomButton(text = "배치도 미리보기", primary = false, modifier = Modifier.weight(1f), onClick = {})
                BottomButton(text = "계획 진행하기", primary = true, modifier = Modifier.weight(1f), onClick = {})
            }
        }
    }
}

/** 탑승 인원 N 명 / 운전자 1명 · 고정 / 동승자 [−] N [+] */
@Composable
private fun PassengerCard(total: Int, companions: Int, enabled: Boolean, onCompanions: (Int) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, CardBorder, shape)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "탑승 인원", color = PleosTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text(text = "$total 명", color = PleosTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(10.dp))
        Text(text = "운전자 1명 · 고정", color = PleosTextSecondary, fontSize = 17.sp)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(RowBg)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "동승자", color = PleosTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StepButton("−", enabled = enabled && companions > 0) { onCompanions(-1) }
            Text(
                text = "$companions",
                color = PleosTextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            StepButton("+", enabled = enabled && companions < PlanDetailUiState.MAX_COMPANIONS) { onCompanions(1) }
        }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 60.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .shadow(3.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(StepTop, StepBottom)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = PleosNavy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

/** 가져갈 짐 N + 가로 × 세로 × 높이 / 짐 줄 목록 */
@Composable
private fun ItemsCard(state: PlanDetailUiState, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, CardBorder, shape)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "가져갈 짐 ", color = PleosTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = "${state.itemCount}", color = CountBlue, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text(text = "가로 × 세로 × 높이", color = PleosTextSecondary, fontSize = 16.sp)
        }
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.items.forEach { item -> ItemRow(item = item, enabled = !state.saving) { onEdit(item.itemId) } }
        }
    }
}

@Composable
private fun ItemRow(item: PlanItemUi, enabled: Boolean, onEdit: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RowBg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 짐 사진이 없어 기본 상자 그림
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Image(painter = painterResource(R.drawable.ic_item_box), contentDescription = null, modifier = Modifier.size(72.dp))
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, color = PleosTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(text = item.countLabel, color = PleosTextSecondary, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${item.widthCm}  ×  ${item.depthCm}  ×  ${item.heightCm}  cm",
                color = PleosTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 64.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                .clickable(enabled = enabled, onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "수정", color = PleosNavy, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun BottomButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .height(84.dp)
            .shadow(4.dp, shape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .clip(shape)
            .background(if (primary) Brush.verticalGradient(listOf(PrimaryTop, PrimaryBottom)) else Brush.verticalGradient(listOf(Color.White, SecondaryBottom)))
            .border(1.dp, if (primary) Color.Transparent else CardBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = if (primary) Color.White else PleosNavy, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private val CardBorder = Color(0xFFE4EAF3)
private val RowBg = Color(0xFFF1F5FB)
private val CountBlue = Color(0xFF2E6BD6)
private val StepTop = Color(0xFFF2F6FC)
private val StepBottom = Color(0xFFDCE5F2)
private val PrimaryTop = Color(0xFF24477A)
private val PrimaryBottom = Color(0xFF14305A)
private val SecondaryBottom = Color(0xFFF0F5FC)
private val ShadowColor = Color(0x331B2B4B)
