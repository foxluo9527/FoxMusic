package com.fox.music.feature.social

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.social.Post
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.PostCommentPreview
import com.fox.music.core.ui.component.PostFeedMediaAspectRatio
import com.fox.music.core.ui.component.PostMediaCarousel
import com.fox.music.core.ui.component.PostMoreBottomSheet
import com.fox.music.core.ui.component.PostMusicBar
import com.fox.music.core.ui.component.PostTagsRow
import com.fox.music.core.ui.theme.FoxEditorial
import com.fox.music.core.ui.theme.InkQuaternary
import com.fox.music.core.ui.theme.InkTertiary
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.core.ui.theme.foxEditorialAccent
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.DurationUnit

/**
 * 广场列表 item（v11 编辑排版）。
 *
 * 与 [com.fox.music.core.ui.component.PostCard] 的差异（后者被个人页 / 用户主页 /
 * 帖子详情 4 处复用，因此**不改它**，而是新建本组件供广场列表专用）：
 * - 拆掉卡片墙：无 surface 卡片背景、无投影，条目之间用 1px 发丝线分隔；
 * - 标题改衬线 18sp，与 14.5sp/1.65 正文拉开层级（旧 titleMedium 与 bodyMedium 只差一级）；
 * - 互动行左对齐、间距 22dp（旧 SpaceEvenly 三等分导致均质失焦）；
 * - 区块间距统一 12dp，条目内边距 20dp；
 * - 全部颜色走令牌（含深底令牌），无硬编码色、无 alpha 叠加容器色。
 */
@Composable
fun FoxEditorialPostItem(
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
    val accent = foxEditorialAccent
    val ink2 = FoxInk2
    val ink3 = FoxInk3
    val ink4 = FoxInk4

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPostClick),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            FoxEditorialPostHeader(
                post = post,
                ink4 = ink4,
                onUserClick = onUserClick,
                onMoreClick = { showMoreSheet = true },
            )

            val title = post.title?.takeIf { it.isNotBlank() && it != "无标题" }
            if (title != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    // 衬线 18sp / 行高 1.25 / 紧排 -0.5sp：比正文大两级，主次一眼可辨
                    style = FoxEditorial.articleTitle.copy(
                        fontSize = 18.sp,
                        lineHeight = 22.5.sp,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val bodyText = post.content.trim().takeIf { content ->
                content.isNotBlank() && content != title
            }
            if (bodyText != null) {
                // 标题与正文同属一个文字块，按设计稿 CSS 收紧到 6dp
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 14.5.sp,
                        lineHeight = 24.sp,
                    ),
                    color = ink2,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val displayMedia = post.displayMedia
            if (displayMedia.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                PostMediaCarousel(
                    media = displayMedia,
                    onMediaClick = onImageClick,
                    cornerRadius = 10.dp,
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

            if (post.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                PostTagsRow(tags = post.tags.map { it.name }, onTagClick = onTagClick)
            }

            previewComment?.let { comment ->
                Spacer(modifier = Modifier.height(12.dp))
                PostCommentPreview(
                    comment = comment,
                    onClick = onPostClick,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            FoxEditorialPostInteractionRow(
                post = post,
                accent = accent,
                ink3 = ink3,
                onLikeClick = onLikeClick,
                onCommentClick = onCommentClick,
                onShareClick = onShareClick,
            )
        }
        HorizontalDivider(thickness = 1.dp, color = FoxHairline)
    }
}

/** 作者行：36dp 圆形头像 + 13sp 粗昵称 + 11sp 相对时间（昵称下方）+ 右侧线性三点。 */
@Composable
private fun FoxEditorialPostHeader(
    post: Post,
    ink4: Color,
    onUserClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
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
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                placeholderIcon = Icons.Default.Person,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.authorName ?: "未知用户",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatPostItemTime(post.createdAt),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = ink4,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onMoreClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(17.dp)) {
                val radius = 1.3.dp.toPx()
                val stroke = Stroke(width = 1.7.dp.toPx())
                val cx = size.width / 2f
                listOf(0.16f, 0.5f, 0.84f).forEach { fraction ->
                    drawCircle(
                        color = ink4,
                        radius = radius,
                        center = Offset(cx, size.height * fraction),
                        style = stroke,
                    )
                }
            }
        }
    }
}

/**
 * 互动行：左对齐、间距 22dp（旧实现 SpaceEvenly 三等分，视觉失焦）。
 * 每项 = 16dp 线性图标 + 11.5sp 计数（tabular-nums），命中区 44dp。
 */
@Composable
private fun FoxEditorialPostInteractionRow(
    post: Post,
    accent: Color,
    ink3: Color,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoxEditorialPostAction(
            icon = if (post.isLiked) Icons.Default.Favorite else Icons.Outlined.Favorite,
            text = formatPostItemCount(post.likeCount),
            tint = if (post.isLiked) accent else ink3,
            onClick = onLikeClick,
        )
        FoxEditorialPostAction(
            icon = Icons.Outlined.ModeComment,
            text = formatPostItemCount(post.commentCount),
            tint = ink3,
            onClick = onCommentClick,
        )
        FoxEditorialPostAction(
            icon = Icons.Outlined.Share,
            text = "分享",
            tint = ink3,
            onClick = onShareClick,
        )
    }
}

@Composable
private fun FoxEditorialPostAction(
    icon: ImageVector,
    text: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            // tabular-nums：数字等宽，翻页时计数不会左右抖动
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                fontFeatureSettings = "tnum",
            ),
            color = tint,
        )
    }
}

// ── 深底令牌 ────────────────────────────────────────────────────────────────
// 仓库现成的 InkTertiary / InkQuaternary / PaperHairline 只有浅色值（v10 组件直接
// 用它们，深色模式下会变成浅色发丝线压在深底上）。这里按 colorScheme 亮暗取设计稿
// 对应的深底令牌，保证「同一套排版在深色令牌下成立」。
// 判定方式：所有浅色预设的 onSurface 都是深墨（#1A1512 一类），所有深色预设的
// onSurface 都是奶白（#F3EDE3 一类），用亮度切分对 12 套预设 + 自定义取色都成立。

private val isFoxDarkPaper: Boolean
    @Composable get() = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f

/** ink2：正文 */
private val FoxInk2: Color
    @Composable get() = if (isFoxDarkPaper) Color(0xFFC9BFAF) else Color(0xFF5A544C)

/** ink3：互动行、计数 */
private val FoxInk3: Color
    @Composable get() = if (isFoxDarkPaper) Color(0xFFA99F92) else InkTertiary

/** ink4：相对时间、三点 */
private val FoxInk4: Color
    @Composable get() = if (isFoxDarkPaper) Color(0xFF7E7568) else InkQuaternary

/** 发丝线 */
private val FoxHairline: Color
    @Composable get() = if (isFoxDarkPaper) Color(0xFF3A322A) else PaperHairline

private fun formatPostItemTime(createdAt: String?): String {
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

private fun formatPostItemCount(count: Int): String {
    // KMP 没有 String.format，手算一位小数
    return when {
        count < 1000 -> count.toString()
        count < 10000 -> "${count / 1000}.${(count % 1000) / 100}k"
        else -> "${count / 10000}.${(count % 10000) / 1000}w"
    }
}
