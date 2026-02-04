package com.fox.music.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.fox.music.core.ui.modifier.BlurTransformation

/**
 *    Author : 罗福林
 *    Date   : 2026/2/4
 *    Desc   :
 */
@Composable
fun BlurBgImage(modifier: Modifier = Modifier,url: String?) {
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(processUrl(url))
            .transformations(BlurTransformation(radius = 90, scale = 1f))
            .diskCacheKey(url)
            .memoryCacheKey(url)
            .build()
    )
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

private fun processUrl(url: String?): String? {
    return url?.let {
        if (it.startsWith("http")) it else "http://39.106.30.151:9000$it"
    }
}