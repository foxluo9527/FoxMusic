package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.fox.music.core.common.util.MediaUrlResolver
import coil.compose.SubcomposeAsyncImage

@Composable
fun CachedImage(
    imageUrl: String?,
    errorImageUrl:String?=null,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    placeholderIcon: ImageVector = Icons.Filled.MusicNote,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (imageUrl.isNullOrEmpty()){
        PlaceholderBox(modifier.clip(shape),icon = placeholderIcon)
        return
    }
    var model by remember { mutableStateOf(imageUrl) }
    LaunchedEffect(imageUrl) {
        model = imageUrl
    }
    SubcomposeAsyncImage(
        model = processUrl(model),
        contentDescription = contentDescription,
        modifier = modifier.clip(shape),
        contentScale = contentScale,
        loading = {
            PlaceholderBox(modifier = Modifier.matchParentSize(), icon = placeholderIcon)
        },
        error = {
            PlaceholderBox(modifier = Modifier.matchParentSize(), icon = Icons.Filled.BrokenImage)
        },
        onError = {
            errorImageUrl?.let {
                model = it
            }
        }
    )
}

private fun processUrl(url: String?): Any? {
    if (url.isNullOrBlank()) return null
    return MediaUrlResolver.resolve(url) ?: url
}

@Composable
private fun PlaceholderBox(modifier: Modifier= Modifier,icon: ImageVector) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}
