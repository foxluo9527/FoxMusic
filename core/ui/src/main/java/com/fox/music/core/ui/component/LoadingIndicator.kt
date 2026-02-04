package com.fox.music.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.fox.music.core.ui.R
import com.fox.music.core.ui.theme.FoxMusicTheme

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier.fillMaxSize(),
    useLottie: Boolean = true,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (useLottie) {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(R.raw.loading)
            )
            if (composition != null) {
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.size(120.dp),
                )
            } else {
                FallbackIndicator()
            }
        } else {
            FallbackIndicator()
        }
    }
}

@Composable
private fun FallbackIndicator() {
    CircularProgressIndicator(
        color = MaterialTheme.colorScheme.primary,
    )
}

@Preview
@Composable
private fun LoadingIndicatorPreview() {
    FoxMusicTheme {
        LoadingIndicator(useLottie = false)
    }
}
