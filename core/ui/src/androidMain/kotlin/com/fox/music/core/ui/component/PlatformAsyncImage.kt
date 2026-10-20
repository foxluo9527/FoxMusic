package com.fox.music.core.ui.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import java.io.File

@Composable
actual fun PlatformAsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    onError: () -> Unit,
    loadingContent: @Composable BoxScope.() -> Unit,
    errorContent: @Composable BoxScope.() -> Unit,
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = { loadingContent() },
        error = { errorContent() },
        onError = { onError() },
    )
}

@Composable
actual fun PlatformFileImage(
    filePath: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    SubcomposeAsyncImage(
        model = File(filePath),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
