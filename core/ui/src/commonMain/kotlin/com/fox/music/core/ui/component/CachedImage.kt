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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.fox.music.core.common.util.MediaUrlResolver

/**
 * 带占位/错误兜底的图片。
 *
 * [placeholderColor] 控制加载中占位块的底色。默认 `surfaceVariant`（列表卡片的观感）；
 * 放在纸感渐变背景上的大图（如播放页封面）应传 [Color.Transparent]——
 * 否则封面图加载间隙会露出一块与页面底色不一致的实心方块（用户反馈的「白色块」）。
 */
@Composable
fun CachedImage(
    imageUrl: String?,
    errorImageUrl:String?=null,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    placeholderIcon: ImageVector = Icons.Filled.MusicNote,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (imageUrl.isNullOrEmpty()){
        PlaceholderBox(modifier.clip(shape), icon = placeholderIcon, color = placeholderColor)
        return
    }
    var model by remember { mutableStateOf(imageUrl) }
    LaunchedEffect(imageUrl) {
        model = imageUrl
    }
    val resolved = processUrl(model)
    if (resolved == null) {
        PlaceholderBox(modifier.clip(shape), icon = placeholderIcon, color = placeholderColor)
        return
    }
    PlatformAsyncImage(
        url = resolved,
        contentDescription = contentDescription,
        modifier = modifier.clip(shape),
        contentScale = contentScale,
        onError = {
            errorImageUrl?.let { model = it }
        },
        loadingContent = {
            PlaceholderBox(
                modifier = Modifier.matchParentSize(),
                icon = placeholderIcon,
                color = placeholderColor,
            )
        },
        errorContent = {
            PlaceholderBox(
                modifier = Modifier.matchParentSize(),
                icon = Icons.Filled.BrokenImage,
                color = placeholderColor,
            )
        },
    )
}

private fun processUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return MediaUrlResolver.resolve(url) ?: url
}

@Composable
private fun PlaceholderBox(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(
        modifier = modifier
            .background(color),
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
