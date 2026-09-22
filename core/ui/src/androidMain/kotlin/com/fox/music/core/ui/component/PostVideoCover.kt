package com.fox.music.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.common.util.extractVideoFrameBitmap
import com.fox.music.core.model.social.PostMedia

@Composable
fun PostVideoCover(
    media: PostMedia,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showPlayIcon: Boolean = true,
) {
    val context = LocalContext.current
    val imageCover = media.imageCoverUrl?.takeIf { it.isNotBlank() }
    val videoUrl = media.playbackUrl
    val resolvedVideo = rememberResolvedUrl(videoUrl)

    if (!imageCover.isNullOrBlank()) {
        Box(modifier = modifier) {
            CachedImage(
                imageUrl = imageCover,
                contentDescription = "视频封面",
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                contentScale = contentScale,
            )
            if (showPlayIcon) {
                PostVideoPlayIcon(modifier = Modifier.align(Alignment.Center))
            }
        }
        return
    }

    val frameBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, resolvedVideo) {
        value = resolvedVideo?.let { extractVideoFrameBitmap(context, it) }
    }

    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.08f)),
    ) {
        if (frameBitmap != null) {
            Image(
                bitmap = frameBitmap!!.asImageBitmap(),
                contentDescription = "视频封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
        if (showPlayIcon) {
            PostVideoPlayIcon(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun PostVideoPlayIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = "播放视频",
        tint = Color.White,
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(8.dp),
    )
}

@Composable
private fun rememberResolvedUrl(url: String?): String? {
    return androidx.compose.runtime.remember(url) {
        url?.let { MediaUrlResolver.resolve(it) ?: it }
    }
}
