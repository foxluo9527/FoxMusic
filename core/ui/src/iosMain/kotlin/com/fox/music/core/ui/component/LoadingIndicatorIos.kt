package com.fox.music.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun LoadingIndicator(
    modifier: Modifier,
    useLottie: Boolean,
    useSkeleton: Boolean,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (useSkeleton) {
            FoxSkeletonList(rows = 6)
        } else {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
