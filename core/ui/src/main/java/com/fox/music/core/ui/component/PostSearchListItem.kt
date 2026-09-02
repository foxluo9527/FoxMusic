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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.social.Post

@Composable
fun PostSearchListItem(
    post: Post,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlightTag: String? = null,
    highlightQuery: String = "",
) {
    val cover = post.displayMedia.firstOrNull()
    val title = post.title?.takeIf { it.isNotBlank() }
    val contentPreview = post.content.trim().replace("\n", " ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (cover != null) {
            val coverAspect = rememberImageAspectRatio(
                url = cover.displayUrl,
                fallback = 1f,
            )
            CachedImage(
                imageUrl = cover.displayUrl,
                contentDescription = post.title,
                modifier = Modifier
                    .width(88.dp)
                    .aspectRatio(coverAspect.coerceIn(0.6f, 1.4f)),
                shape = RoundedCornerShape(8.dp),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = post.authorName.orEmpty().ifBlank { "用户" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (title != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildHighlightedText(title, highlightQuery, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (contentPreview.isNotEmpty()) {
                Spacer(Modifier.height(if (title != null) 4.dp else 6.dp))
                Text(
                    text = buildHighlightedText(
                        contentPreview,
                        highlightQuery,
                        MaterialTheme.colorScheme.primary,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (post.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row {
                    post.tags.take(3).forEach { tag ->
                        val isHighlighted = highlightTag != null &&
                            tag.name.equals(highlightTag, ignoreCase = true)
                        Text(
                            text = "#${tag.name}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                            color = if (isHighlighted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
            post.music?.let { music ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = buildString {
                            append(music.title)
                            val artist = music.artists.joinToString("/") { it.name }
                            if (artist.isNotBlank()) {
                                append(" · ")
                                append(artist)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = post.likeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = post.commentCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun PostSearchTagFilterChip(
    tag: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#$tag",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "清除标签筛选",
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onClear),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildHighlightedText(text: String, query: String, highlightColor: Color) = buildAnnotatedString {
    val keyword = query.trim()
    if (keyword.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    var startIndex = 0
    val lowerText = text.lowercase()
    val lowerQuery = keyword.lowercase()
    while (true) {
        val index = lowerText.indexOf(lowerQuery, startIndex)
        if (index < 0) {
            append(text.substring(startIndex))
            break
        }
        append(text.substring(startIndex, index))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
            append(text.substring(index, index + keyword.length))
        }
        startIndex = index + keyword.length
    }
}
