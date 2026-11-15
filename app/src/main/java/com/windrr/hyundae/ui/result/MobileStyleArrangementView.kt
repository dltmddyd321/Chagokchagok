package com.windrr.hyundae.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windrr.hyundae.R
import kotlin.math.roundToInt

/**
 * 모바일(서브앱) 배치 추천과 같은 모양의 투싼 탑뷰:
 * 좌석 표시칸("탑승"/"좌석", 접는 좌석은 칸을 지움) + 짐은 파란 점선 칸. 넣는 방향 화살표는 없음.
 */
@Composable
fun MobileStyleArrangementView(layout: ArrangementLayoutUi, modifier: Modifier = Modifier) {
    val image = ImageBitmap.imageResource(R.drawable.img_vehicle_tucson_top_view)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(VehicleTopView.CROP_W.toFloat() / VehicleTopView.CROP_H)
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
            val scale = maxWidth / VehicleTopView.CROP_W.toFloat()
            layout.seats.forEach { seat ->
                Mark(seat.rect, scale, if (seat.mark == SeatMark.BOARDING) BoardingFill else EmptySeatFill, if (seat.mark == SeatMark.BOARDING) "탑승" else "좌석")
            }
            layout.items.forEach { item -> Mark(item.rect, scale, ItemFill, item.name) }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = "트렁크 입구", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Mark(rect: PxRect, scale: Dp, fill: Color, label: String) {
    Box(
        modifier = Modifier
            .offset(x = scale * (rect.left - VehicleTopView.CROP_LEFT), y = scale * (rect.top - VehicleTopView.CROP_TOP))
            .size((scale * rect.width).coerceAtLeast(18.dp), (scale * rect.height).coerceAtLeast(18.dp))
            .clip(RoundedCornerShape(5.dp))
            .background(fill)
            .drawBehind {
                val inset = 0.75.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** 접는 좌석 안내: "2열 좌석을 모두 접은 배치예요." 등. 접지 않으면 null */
fun foldDescription(layout: ArrangementLayoutUi): String? {
    val folded = layout.foldedGroups.flatMap { it.seats }.toSet()
    if (folded.isEmpty()) return null
    val allSecondRow = folded.containsAll(
        listOf(
            com.windrr.hyundae.data.local.entity.SeatCode.ROW_2_LEFT,
            com.windrr.hyundae.data.local.entity.SeatCode.ROW_2_CENTER,
            com.windrr.hyundae.data.local.entity.SeatCode.ROW_2_RIGHT,
        ),
    )
    val names = if (allSecondRow) "2열 좌석을 모두" else layout.foldedGroups.joinToString(", ") { seatGroupName(it.seats) }.let { "$it${Josa.을를(it)}" }
    return "$names 접은 배치예요."
}

// 서브앱 배치 추천과 같은 색
private val BoardingFill = Color(0xCC5D6672)
private val EmptySeatFill = Color(0xB38C9199)
private val ItemFill = Color(0xE62C5DA6)
