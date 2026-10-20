package com.fox.music.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.social.Post
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.DurationUnit

@Composable
fun PostCard(
    post: Post,
    onPostClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onUserClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewComment: Comment? = null,
    isOwnPost: Boolean = false,
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onReportClick: () -> Unit = {},
    onImageClick: (Int) -> Unit = {},
    onMusicPlayClick: () -> Unit = {},
    onMusicRowClick: () -> Unit = onMusicPlayClick,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    onTagClick: (String) -> Unit = {},
) {
    var showMoreSheet by remember { mutableStateOf(false) }

    if (showMoreSheet) {
        PostMoreBottomSheet(
            isOwnPost = isOwnPost,
            onDismiss = { showMoreSheet = false },
            onEdit = onEditClick,
            onDelete = onDeleteClick,
            onReport = onReportClick,
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        PostContentBody(
            post = post,
            previewComment = previewComment,
            onPostClick = onPostClick,
            onImageClick = onImageClick,
            onUserClick = onUserClick,
            showMoreButton = true,
            onMoreClick = { showMoreSheet = true },
            onLikeClick = onLikeClick,
            onCommentClick = onCommentClick,
            onShareClick = onShareClick,
            onMusicPlayClick = onMusicPlayClick,
            onMusicRowClick = onMusicRowClick,
            isCurrentTrack = isCurrentTrack,
            isPlaying = isPlaying,
            onTagClick = onTagClick,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
fun PostContentBody(
    post: Post,
    onPostClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onUserClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewComment: Comment? = null,
    showMoreButton: Boolean = false,
    onMoreClick: () -> Unit = {},
    onImageClick: (Int) -> Unit = {},
    onMusicPlayClick: () -> Unit = {},
    onMusicRowClick: () -> Unit = onMusicPlayClick,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    onTagClick: (String) -> Unit = {},
) {
    val displayMedia = post.displayMedia
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onUserClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CachedImage(
                    imageUrl = post.authorAvatar,
                    contentDescription = post.authorName,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    placeholderIcon = Icons.Default.Person,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = post.authorName ?: "未知用户",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = formatPostTime(post.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showMoreButton) {
                IconButton(onClick = onMoreClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
            }
        }

        if (displayMedia.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            PostMediaCarousel(
                media = displayMedia,
                onMediaClick = onImageClick,
                minAspectRatio = PostFeedMediaAspectRatio.LIST_MIN,
                maxAspectRatio = PostFeedMediaAspectRatio.MAX,
                cropOverflow = true,
            )
        }

        post.music?.let { music ->
            Spacer(modifier = Modifier.height(12.dp))
            PostMusicBar(
                music = music,
                isCurrentTrack = isCurrentTrack,
                isPlaying = isPlaying,
                onPlayClick = onMusicPlayClick,
                onClick = onMusicRowClick,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val title = post.title?.takeIf { it.isNotBlank() && it != "无标题" }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPostClick),
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        val bodyText = post.content.trim().takeIf { content ->
            content.isNotBlank() && content != title
        }
        if (bodyText != null) {
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPostClick),
            )
        }

        if (post.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            PostTagsRow(tags = post.tags.map { it.name }, onTagClick = onTagClick)
        }

        previewComment?.let { comment ->
            PostCommentPreview(
                comment = comment,
                onClick = onPostClick,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PostInteractionBar(
            post = post,
            onLikeClick = onLikeClick,
            onCommentClick = onCommentClick,
            onShareClick = onShareClick,
        )
    }
}

@Composable
fun PostInteractionBar(
    post: Post,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PostActionItem(
            modifier = Modifier.weight(1f),
            icon = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            tint = if (post.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            count = post.likeCount,
            onClick = onLikeClick,
        )
        PostActionItem(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.ChatBubbleOutline,
            count = post.commentCount,
            onClick = onCommentClick,
        )
        PostActionItem(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Share,
            onClick = onShareClick,
            showCount = false,
        )
    }
}

@Composable
private fun PostActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    count: Int = 0,
    showCount: Boolean = true,
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
        if (showCount) {
            Text(
                text = formatCount(count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PostImageGrid(
    images: List<String>,
    onImageClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageCount = images.size.coerceAtMost(9)
    val displayImages = images.take(imageCount)
    val columns = when (imageCount) {
        1, 2 -> imageCount
        4 -> 2
        else -> 3
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (imageCount == 1) {
            CachedImage(
                imageUrl = displayImages[0],
                contentDescription = "动态图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable { onImageClick(0) },
                shape = MaterialTheme.shapes.small,
            )
            return@Column
        }

        displayImages.chunked(columns).forEachIndexed { rowIndex, rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rowImages.forEachIndexed { colIndex, imageUrl ->
                    val globalIndex = rowIndex * columns + colIndex
                    CachedImage(
                        imageUrl = imageUrl,
                        contentDescription = "动态图片",
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clickable { onImageClick(globalIndex) },
                        shape = MaterialTheme.shapes.small,
                    )
                }
                repeat(columns - rowImages.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun formatPostTime(createdAt: String?): String {
    if (createdAt == null) return "刚刚"
    return try {
        // java.time 是 JVM-only（K/N 不可用），跨端统一走 kotlinx-datetime
        val postTime = Instant.parse(createdAt)
        val minutes = (Clock.System.now() - postTime).toLong(DurationUnit.MINUTES)
        when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            minutes < 1440 -> "${minutes / 60}小时前"
            minutes < 10080 -> "${minutes / 1440}天前"
            else -> {
                val local = postTime.toLocalDateTime(TimeZone.currentSystemDefault())
                "${local.monthNumber.pad2()}-${local.dayOfMonth.pad2()}"
            }
        }
    } catch (_: Exception) {
        "刚刚"
    }
}

private fun Int.pad2(): String = if (this < 10) "0$this" else toString()

private fun formatCount(count: Int): String {
    // KMP 没有 String.format，手算一位小数
    return when {
        count < 1000 -> count.toString()
        count < 10000 -> "${count / 1000}.${(count % 1000) / 100}k"
        else -> "${count / 10000}.${(count % 10000) / 1000}w"
    }
}
