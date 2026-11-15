package com.windrr.hyundae.ui.plan

import android.widget.Toast
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import com.windrr.hyundae.ui.result.LoadCompleteScreen
import com.windrr.hyundae.ui.result.LoadCompletion
import com.windrr.hyundae.ui.result.LoadGuideScreen
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
import com.windrr.hyundae.ui.result.MobileStyleArrangementView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
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
    /** 차량 트렁크 열림 여부 (Vehicle SDK). 가이드에서 열렸다가 닫히면 적재 완료 */
    trunkOpen: Boolean?,
    /** 적재 완료 > 지도 열기 */
    onOpenMap: () -> Unit,
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
    when (val run = state.run) {
        is PlanRunState.Preparing -> {
            BackHandler {}
            PlanPreparingScreen(run.text)
        }
        is PlanRunState.Guide -> {
            BackHandler(onBack = viewModel::closeRun)
            // 트렁크가 "열렸다가 닫히면" 적재 완료. 가이드에 들어올 때 이미 닫혀 있던 건 완료로 보지 않는다
            var trunkWasOpen by remember { mutableStateOf(false) }
            LaunchedEffect(trunkOpen) {
                when (trunkOpen) {
                    true -> trunkWasOpen = true
                    false -> if (trunkWasOpen) viewModel.completeRun(LoadCompletion.TRUNK_CLOSED)
                    null -> Unit
                }
            }
            LoadGuideScreen(
                option = run.option,
                steps = run.steps,
                onChangeCondition = viewModel::closeRun,
                onFinished = { viewModel.completeRun(LoadCompletion.BUTTON) },
            )
        }
        is PlanRunState.Complete -> {
            BackHandler(onBack = viewModel::closeRun)
            LoadCompleteScreen(
                option = run.option,
                items = run.items,
                completion = run.completion,
                onGoHome = {
                    viewModel.closeRun()
                    onBack()
                },
                onOpenMap = onOpenMap,
            )
        }
        null -> {
            PlanDetailScreen(
                state = state,
                onBack = onBack,
                onCompanions = viewModel::changeCompanions,
                onEdit = viewModel::openEdit,
                onPreview = viewModel::openPreview,
                onProceed = viewModel::proceed,
            )
            state.editing?.let { ManualInputDialog(input = it, onIntent = viewModel::onEditIntent) }
            state.preview?.let { PlanPreviewDialog(preview = it, onClose = viewModel::closePreview) }
            if (state.confirmProceed) {
                ProceedConfirmDialog(onCancel = viewModel::dismissConfirm, onConfirm = viewModel::confirmProceed)
            }
        }
    }
}

/** 변경한 계획 저장·재계산·안내 받는 중 (전체 로딩) */
@Composable
private fun PlanPreparingScreen(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = PleosNavy, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(28.dp))
        Text(text = text, color = PleosTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(text = "잠시만 기다려 주세요", color = PleosTextSecondary, fontSize = 17.sp)
    }
}

/** 변경한 계획으로 진행할까요? (취소 / 저장하고 진행) */
@Composable
private fun ProceedConfirmDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White)
                .padding(horizontal = 40.dp, vertical = 36.dp),
        ) {
            Text(text = "변경한 계획으로 진행할까요?", color = PleosTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Text(text = "변경한 인원과 짐에 맞춰 배치를 다시 계산해요.", color = PleosTextSecondary, fontSize = 18.sp)
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BottomButton(text = "취소", primary = false, modifier = Modifier.weight(1f), onClick = onCancel)
                BottomButton(text = "저장하고 진행", primary = true, modifier = Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

@Composable
fun PlanDetailScreen(
    state: PlanDetailUiState,
    onBack: () -> Unit,
    onCompanions: (Int) -> Unit,
    onEdit: (String) -> Unit,
    onPreview: () -> Unit,
    onProceed: () -> Unit,
) {
    // 내비와 화면을 나눠 쓰면(1:1, 1:1:1) 폭이 좁아진다 → 세로 배치 (시안: 컴팩트 내 계획)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < COMPACT_WIDTH) {
            CompactPlanDetail(state, onCompanions, onEdit, onPreview, onProceed)
        } else {
            WidePlanDetail(state, onBack, onCompanions, onEdit, onPreview, onProceed)
        }
    }
}

/** 제목 영역 (공통): 내계획 / 상세정보 · 계획 이름 · 안내 */
@Composable
private fun PlanHeader(state: PlanDetailUiState) {
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
}

/** 좁은 화면: 탑승 인원 → 가져갈 짐(스크롤) → 배치도 미리보기 · 계획 진행하기. 차 사진·뒤로 가기는 뺀다 */
@Composable
private fun CompactPlanDetail(
    state: PlanDetailUiState,
    onCompanions: (Int) -> Unit,
    onEdit: (String) -> Unit,
    onPreview: () -> Unit,
    onProceed: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 20.dp),
    ) {
        PlanHeader(state)
        Spacer(Modifier.height(20.dp))
        PassengerCard(
            total = state.totalPassengers,
            companions = state.companions,
            enabled = state.planId != null && !state.saving,
            onCompanions = onCompanions,
            compact = true,
        )
        Spacer(Modifier.height(20.dp))
        ItemsCard(
            state = state,
            onEdit = onEdit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BottomButton(text = "배치도 미리보기", primary = false, modifier = Modifier.weight(1f), onClick = { if (state.planId != null) onPreview() })
            BottomButton(text = "계획 진행하기", primary = true, modifier = Modifier.weight(1f), onClick = { if (state.planId != null) onProceed() })
        }
    }
}

@Composable
private fun WidePlanDetail(
    state: PlanDetailUiState,
    onBack: () -> Unit,
    onCompanions: (Int) -> Unit,
    onEdit: (String) -> Unit,
    onPreview: () -> Unit,
    onProceed: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 24.dp),
    ) {
        PlanHeader(state)
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
                BottomButton(text = "배치도 미리보기", primary = false, modifier = Modifier.weight(1f), onClick = { if (state.planId != null) onPreview() })
                BottomButton(text = "계획 진행하기", primary = true, modifier = Modifier.weight(1f), onClick = { if (state.planId != null) onProceed() })
            }
        }
    }
}

/** 배치도 미리보기 (시안): 선택한 적재 배치 탑뷰 + 접는 좌석 안내 */
@Composable
private fun PlanPreviewDialog(preview: PlanPreviewState, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.66f)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White)
                .padding(horizontal = 44.dp, vertical = 36.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "배치도 미리보기", color = PleosTextPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                    Text(text = "짐을 싣기 전에 위치와 방향을 확인해 주세요.", color = PleosTextSecondary, fontSize = 17.sp)
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "✕", color = PleosTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(0.8f)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(20.dp))
                    .background(PreviewPanel)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                Text(text = "선택한 적재 배치", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(PreviewDivider),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (preview) {
                        PlanPreviewState.Loading -> CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                        is PlanPreviewState.Message -> Text(text = preview.text, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
                        is PlanPreviewState.Ready -> MobileStyleArrangementView(layout = preview.layout, modifier = Modifier.fillMaxHeight())
                    }
                }
            }
            (preview as? PlanPreviewState.Ready)?.foldText?.let {
                Spacer(Modifier.height(18.dp))
                Text(text = it, color = PleosTextSecondary, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

/** 탑승 인원 N 명 / 운전자 1명 · 고정 / 동승자 [−] N [+] */
@Composable
private fun PassengerCard(total: Int, companions: Int, enabled: Boolean, onCompanions: (Int) -> Unit, compact: Boolean = false) {
    val shape = RoundedCornerShape(24.dp)
    if (compact) {
        // 시안(컴팩트): 위 줄 탑승 인원 · N 명, 아래 줄 운전자 1명 · 고정 ↔ 동승자 스테퍼
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color.White)
                .border(1.dp, CardBorder, shape)
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "탑승 인원", color = PleosTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(text = "$total 명", color = PleosTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "운전자 1명 · 고정", color = PleosTextSecondary, fontSize = 17.sp, modifier = Modifier.weight(1f))
                CompanionStepper(companions, enabled, onCompanions, compact = true)
            }
        }
        return
    }
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
        CompanionStepper(companions, enabled, onCompanions, compact = false, modifier = Modifier.fillMaxWidth())
    }
}

/** 동승자 [−] N [+] 칸. 넓은 화면은 카드 폭에 맞추고, 컴팩트는 내용만큼 */
@Composable
private fun CompanionStepper(companions: Int, enabled: Boolean, onCompanions: (Int) -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(RowBg)
            .border(1.dp, if (compact) CardBorder else Color.Transparent, RoundedCornerShape(16.dp))
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "동승자",
            color = PleosTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = if (compact) Modifier.padding(start = 8.dp, end = 20.dp) else Modifier.weight(1f),
        )
        StepButton("−", enabled = enabled && companions > 0, compact = compact) { onCompanions(-1) }
        Text(
            text = "$companions",
            color = PleosTextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = if (compact) 22.dp else 32.dp),
        )
        StepButton("+", enabled = enabled && companions < PlanDetailUiState.MAX_COMPANIONS, compact = compact) { onCompanions(1) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(width = if (compact) 56.dp else 72.dp, height = if (compact) 54.dp else 60.dp)
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

/** 이보다 좁으면 컴팩트 배치 (홈과 같은 기준) */
private val COMPACT_WIDTH = 900.dp

private val CardBorder = Color(0xFFE4EAF3)
private val PreviewPanel = Color(0xFF0B1A33)
private val PreviewDivider = Color(0xFF23406A)
private val RowBg = Color(0xFFF1F5FB)
private val CountBlue = Color(0xFF2E6BD6)
private val StepTop = Color(0xFFF2F6FC)
private val StepBottom = Color(0xFFDCE5F2)
private val PrimaryTop = Color(0xFF24477A)
private val PrimaryBottom = Color(0xFF14305A)
private val SecondaryBottom = Color(0xFFF0F5FC)
private val ShadowColor = Color(0x331B2B4B)
