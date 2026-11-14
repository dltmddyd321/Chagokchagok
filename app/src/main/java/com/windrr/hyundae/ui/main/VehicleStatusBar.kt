package com.windrr.hyundae.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windrr.hyundae.R
import com.windrr.hyundae.ui.home.PleosTextPrimary

/**
 * 화면 오른쪽 위 차량 상태: "P · 주차 중"  [트렁크 닫힘].
 * Vehicle SDK 기어·트렁크(REAR 도어) 신호를 그대로 보여주고, 신호가 없으면 숨긴다. 모든 탭 공통.
 */
@Composable
fun VehicleStatusBar(gearLabel: String?, trunkLabel: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        gearLabel?.let {
            Text(text = it, color = PleosTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(24.dp))
        }
        trunkLabel?.let {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ChipBg)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_car_outline),
                    contentDescription = null,
                    tint = PleosTextPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = it, color = PleosTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val ChipBg = Color(0xFFE6EBF3)
