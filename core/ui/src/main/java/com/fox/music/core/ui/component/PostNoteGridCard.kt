package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.social.PostMediaType

@Composable
fun PostNoteGridCard(
    post: Post,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onTagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cover = post.displayMedia.firstOrNull()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        if (cover != null) {
            val fallbackAspect = if (cover.type == PostMediaType.VIDEO) 3f / 4f else 1f
            val (coverAspect, aspectReady) = rememberPostMediaAspectRatioLoaded(
                media = cover,
                fallback = fallbackAspect,
                min = PostFeedMediaAspectRatio.MIN,
                max = PostFeedMediaAspectRatio.MAX,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (aspectReady) {
                            Modifier.aspectRatio(coverAspect)
                        } else {
                            Modifier.height(120.dp)
                        },
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                when (cover.type) {
                    PostMediaType.VIDEO -> {
                        PostVideoCover(
                            media = cover,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                            showPlayIcon = true,
                        )
                    }
                    else -> {
                        CachedImage(
                            imageUrl = cover.displayUrl,
                            contentDescription = post.title,
                            modifier = Modifier.matchParentSize(),
                            shape = RectangleShape,
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                if (cover.type == PostMediaType.LIVE) {
                    PostLiveBadge(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            post.music?.let { music ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = music.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            val title = post.title?.takeIf { it.isNotBlank() && it != "无标题" } ?: post.content
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (post.tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = post.tags.joinToString(" ") { "#${it.name}" },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        onTagClick(post.tags.first().name)
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CachedImage(
                        imageUrl = post.authorAvatar,
                        contentDescription = post.authorName,
                        modifier = Modifier.size(20.dp),
                        shape = CircleShape,
                        placeholderIcon = Icons.Default.Person,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = post.authorName ?: "用户",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onLikeClick),
                ) {
                    Icon(
                        imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "点赞",
                        modifier = Modifier.size(14.dp),
                        tint = if (post.isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = post.likeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
