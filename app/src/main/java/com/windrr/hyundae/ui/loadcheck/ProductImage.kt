package com.windrr.hyundae.ui.loadcheck

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil.compose.SubcomposeAsyncImage
import com.windrr.hyundae.R

/**
 * 제품 사진. 주소가 없거나 불러오지 못하면(로딩 중 포함) 기본 짐 이미지를 [fallbackSize]로 가운데에.
 * 사진은 칸 안에 잘리지 않게 맞춘다.
 */
@Composable
fun ProductImage(
    url: String?,
    fallbackSize: Dp,
    modifier: Modifier = Modifier,
    @DrawableRes fallback: Int = R.drawable.ic_item_box,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val placeholder: @Composable () -> Unit = {
            Image(painter = painterResource(fallback), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(fallbackSize))
        }
        if (url.isNullOrBlank()) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() } },
                error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { placeholder() } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
