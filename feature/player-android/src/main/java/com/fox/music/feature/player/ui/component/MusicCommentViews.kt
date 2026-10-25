package com.fox.music.feature.player.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.social.displayAuthorAvatar
import com.fox.music.core.model.social.displayAuthorName
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.theme.FoxEditorial
import com.fox.music.core.ui.theme.InkQuaternary
import com.fox.music.core.ui.theme.InkTertiary
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.core.ui.theme.foxEditorialAccent
import com.fox.music.feature.player.viewmodel.CommentLikeState
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 音乐评论 · 编辑排版视图（v11）。
 *
 * 底部弹窗 [MusicCommentBottomSheet] 与详情页 MusicCommentDetailScreen 共用同一套渲染，
 * 避免两处回复区样式漂移。沿用 v10 编辑排版体系：无边框（发丝线分隔）、强对比衬线、
 * 强调色只出现在 CTA / 选中态（已赞）上。
 */

/** 评论相对时间：两处共用，避免重复实现。 */
fun formatCommentTime(createdAt: String?): String {
    if (createdAt == null) return "刚刚"
    return try {
        val postTime = ZonedDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME)
        val now = ZonedDateTime.now()
        val minutes = java.time.Duration.between(postTime, now).toMinutes()
        when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            minutes < 1440 -> "${minutes / 60}小时前"
            minutes < 10080 -> "${minutes / 1440}天前"
            else -> postTime.format(DateTimeFormatter.ofPattern("MM-dd"))
        }
    } catch (e: Exception) {
        "刚刚"
    }
}

/** 32×4 圆角 drag handle（与全局 sheet 语言一致，实色不用 alpha）。 */
@Composable
fun FoxCommentSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(CircleShape)
                .background(InkQuaternary),
        )
    }
}

/**
 * 评论列表：加载 / 错误 / 空态 / 分页追加态统一走编辑排版。
 *
 * @param onCommentSelected 选中某条评论作为回复目标；传 null 表示不可选（详情页）。
 * @param onLoadMoreReplies 加载更多楼中楼；传 null 时不展示「加载更多」。
 */
@Composable
fun FoxCommentList(
    commentsPaging: LazyPagingItems<Comment>,
    expandedComments: Set<Long>,
    expandedReplies: Map<Long, List<Comment>>,
    loadingReplies: Set<Long>,
    commentLikes: Map<Long, CommentLikeState>,
    onToggleExpand: (Comment) -> Unit,
    onLikeComment: (Comment) -> Unit,
    modifier: Modifier = Modifier,
    onCommentSelected: ((Long, String) -> Unit)? = null,
    onLoadMoreReplies: ((Long, Int) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxSize()) {
        val refreshState = commentsPaging.loadState.refresh

        when (refreshState) {
            is LoadState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(useLottie = false)
                }
            }

            is LoadState.Error -> {
                ErrorView(
                    message = refreshState.error.message ?: "加载失败",
                    onRetry = { commentsPaging.refresh() },
                )
            }

            else -> {
                if (commentsPaging.itemCount == 0) {
                    FoxCommentEmptyState(modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(commentsPaging.itemCount) { index ->
                            commentsPaging[index]?.let { comment ->
                                FoxCommentItem(
                                    comment = comment,
                                    isExpanded = expandedComments.contains(comment.id),
                                    commentLikes = commentLikes,
                                    onToggleExpand = { onToggleExpand(comment) },
                                    onLike = { onLikeComment(comment) },
                                    onSelected = onCommentSelected,
                                    expandedReplies = expandedReplies[comment.id] ?: emptyList(),
                                    isLoadingReplies = loadingReplies.contains(comment.id),
                                    onLoadMoreReplies = onLoadMoreReplies,
                                )
                            }
                        }

                        val appendState = commentsPaging.loadState.append
                        when (appendState) {
                            is LoadState.Loading -> {
                                item {
                                    LoadingIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp),
                                        useLottie = true,
                                    )
                                }
                            }

                            is LoadState.Error -> {
                                item {
                                    ErrorView(
                                        modifier = Modifier.fillMaxWidth(),
                                        message = "加载更多失败",
                                        showIcon = false,
                                        onRetry = { commentsPaging.retry() },
                                    )
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

/** 空态：衬线提示 + 弱化说明，不再是一句纯文本。 */
@Composable
private fun FoxCommentEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "还没有人开口",
            style = FoxEditorial.heroTitle.copy(fontSize = 18.sp, lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "这首歌的第一句评论，会留给先按下发送的人。",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
            ),
            color = InkTertiary,
        )
    }
}

/**
 * 单条评论：32dp 头像 + 昵称与时间同行 + 14.5sp 正文（比昵称大，层级归位）
 * + 右侧线性心形与赞数；项间 1px 发丝线，无卡片背景。
 */
@Composable
fun FoxCommentItem(
    comment: Comment,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onLike: () -> Unit,
    modifier: Modifier = Modifier,
    commentLikes: Map<Long, CommentLikeState> = emptyMap(),
    onSelected: ((Long, String) -> Unit)? = null,
    expandedReplies: List<Comment> = emptyList(),
    isLoadingReplies: Boolean = false,
    onLoadMoreReplies: ((Long, Int) -> Unit)? = null,
) {
    val likeState = commentLikes[comment.id]
    val isLiked = likeState?.isLiked ?: comment.isLiked
    val likeCount = likeState?.likeCount ?: comment.likeCount

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onSelected != null) {
                        Modifier.clickable { onSelected(comment.id, comment.displayAuthorName) }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            CachedImage(
                imageUrl = comment.displayAuthorAvatar,
                contentDescription = comment.displayAuthorName,
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                placeholderIcon = Icons.Default.Person,
            )
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                FoxCommentAuthorLine(
                    name = comment.displayAuthorName,
                    createdAt = comment.createdAt,
                )
                Text(
                    text = comment.content,
                    // 14.5sp / 行高 1.6：必须明显大于 12.5sp 昵称，修正层级倒挂
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.5.sp,
                        lineHeight = 23.2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (comment.replyCount > 0 && !isExpanded) {
                    FoxCommentTextLink(
                        text = "展开 ${comment.replyCount} 条回复",
                        onClick = onToggleExpand,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            FoxCommentLikeButton(
                isLiked = isLiked,
                likeCount = likeCount,
                onClick = onLike,
            )
        }

        if (isExpanded) {
            FoxCommentRepliesBlock(
                replies = expandedReplies,
                isLoading = isLoadingReplies,
                replyCount = comment.replyCount,
                onSelected = onSelected,
                onToggleExpand = onToggleExpand,
                onLoadMoreReplies = { onLoadMoreReplies?.invoke(comment.id, expandedReplies.size) },
                showLoadMore = onLoadMoreReplies != null,
            )
        }

        HorizontalDivider(thickness = 1.dp, color = PaperHairline)
    }
}

/** 昵称 + 时间同一行（省掉整整一行，与帖子评论口径一致）。 */
@Composable
private fun FoxCommentAuthorLine(
    name: String,
    createdAt: String?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = formatCommentTime(createdAt),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 13.sp,
            ),
            color = InkQuaternary,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** 右侧点赞：16dp 线性心形 + 数字（tabular-nums）。已赞=强调色实心，未赞=描边弱化色。 */
@Composable
private fun FoxCommentLikeButton(
    isLiked: Boolean,
    likeCount: Int,
    onClick: () -> Unit,
) {
    val tint = if (isLiked) foxEditorialAccent else InkQuaternary
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isLiked) "取消赞" else "点赞",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = likeCount.toString(),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                fontFeatureSettings = "tnum",
            ),
            color = tint,
        )
    }
}

/** 行内强调色文字链接，替代原来整宽 outlined Button。 */
@Composable
private fun FoxCommentTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
        ),
        fontWeight = FontWeight.Bold,
        color = foxEditorialAccent,
        modifier = modifier.clickable(onClick = onClick),
    )
}

/**
 * 楼中楼：左侧 43dp 缩进 + 2dp 发丝引导竖条（不再用色块背景），子项头像 26dp。
 * 弹窗与详情页共用同一渲染，杜绝两处样式漂移。
 */
@Composable
private fun FoxCommentRepliesBlock(
    replies: List<Comment>,
    isLoading: Boolean,
    replyCount: Int,
    onSelected: ((Long, String) -> Unit)?,
    onToggleExpand: () -> Unit,
    onLoadMoreReplies: () -> Unit,
    showLoadMore: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 43.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)
            .drawBehind {
                drawLine(
                    color = PaperHairline,
                    start = Offset(1.dp.toPx(), 0f),
                    end = Offset(1.dp.toPx(), size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            .padding(start = 13.dp),
    ) {
        replies.forEach { reply ->
            FoxReplyItem(reply = reply, onSelected = onSelected)
        }
        if (isLoading && replies.isEmpty()) {
            LoadingIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                useLottie = false,
            )
        }
        if (showLoadMore && replies.size < replyCount) {
            FoxCommentTextLink(
                text = "加载更多回复",
                onClick = onLoadMoreReplies,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        FoxCommentTextLink(
            text = "收起",
            onClick = onToggleExpand,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 子回复：26dp 头像，正文 13.5sp（比主评论小半档，层级分明）。 */
@Composable
private fun FoxReplyItem(
    reply: Comment,
    onSelected: ((Long, String) -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onSelected != null) {
                    Modifier.clickable { onSelected(reply.id, reply.displayAuthorName) }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CachedImage(
                imageUrl = reply.displayAuthorAvatar,
                contentDescription = reply.displayAuthorName,
                modifier = Modifier.size(26.dp),
                shape = CircleShape,
                placeholderIcon = Icons.Default.Person,
            )
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                FoxCommentAuthorLine(
                    name = reply.displayAuthorName,
                    createdAt = reply.createdAt,
                )
                Text(
                    text = reply.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 20.9.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/**
 * 常驻底部输入条：40dp pill（hairline 描边）+ 右侧强调色「发送」文字钮。
 * 不再点击后弹 AlertDialog。
 */
@Composable
fun FoxCommentInputBar(
    draftText: String,
    onDraftChange: (String) -> Unit,
    onSendClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    replyToUserName: String? = null,
) {
    val pillShape = RoundedCornerShape(20.dp)
    val canSend = draftText.isNotBlank()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = draftText,
            onValueChange = onDraftChange,
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(foxEditorialAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { if (canSend) onSendClick(draftText) },
            ),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, PaperHairline, pillShape)
                        .padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (draftText.isEmpty()) {
                        Text(
                            text = if (replyToUserName != null) "回复 $replyToUserName…" else "说点什么…",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                            ),
                            color = InkQuaternary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .height(44.dp)
                .clickable(enabled = canSend) { onSendClick(draftText) }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "发送",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                // 空草稿时用弱化色而非 alpha 叠加，避免脏色
                color = if (canSend) foxEditorialAccent else InkQuaternary,
            )
        }
    }
}

/** Sheet 头部：kicker + 衬线标题 + 右侧条数，与下方一条发丝线。 */
@Composable
fun FoxCommentSheetHeader(
    commentCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Comments",
                style = FoxEditorial.kicker,
                color = InkQuaternary,
                maxLines = 1,
            )
            Text(
                text = "音乐评论",
                style = FoxEditorial.heroTitle.copy(
                    fontSize = 19.sp,
                    lineHeight = 22.sp,
                    letterSpacing = (-0.5).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            text = "$commentCount 条",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                fontFeatureSettings = "tnum",
            ),
            color = InkQuaternary,
            maxLines = 1,
        )
    }
}

/** 列表内容上下共用的 1px 发丝线。 */
@Composable
fun FoxCommentHairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = PaperHairline)
}
