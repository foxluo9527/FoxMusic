package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.model.music.Music

// 顶圆角对齐全局 sheet 规格 22dp（原来这里写死 20dp，是全仓 4 种圆角之一）
private val MusicSheetCoverShape = RoundedCornerShape(9.dp)
private val MusicSheetIconShape = RoundedCornerShape(10.dp)
private val MusicSheetCapsuleShape = RoundedCornerShape(22.dp)

/**
 * 歌曲「更多」底部弹层（设计稿③）。
 *
 * 旧版是一块没有层级的白板列表：图标风格杂、主次不分、举报和常规操作混在一起。
 * 改版后统一为全局 sheet 模板：
 * - handle + 歌曲头（44dp 封面 9dp 圆角 / 标题 15sp/700 / 艺人 chip：18dp 圆头像 + 11sp 名字）；
 * - 双主钮行：「下一首播放」primary 胶囊 +「添加到歌单」ghost 胶囊；
 * - 常规操作列表（行高 52dp、icon 容器 32dp 圆角 10、底 surfaceVariant）；
 * - 发丝线之后的「反馈」次级区：举报 / 反馈音乐不匹配 / 音源纠错，icon 不套容器、文字走 ink3。
 *
 * 业务回调签名与接线完全不变，只是重排视觉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicActionBottomSheet(
    music: Music,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onReport: () -> Unit,
    onArtistClick: (Long) -> Unit,
    isAdmin: Boolean = false,
    onFeedbackMismatch: () -> Unit = {},
    onCorrectSource: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showDownload = onDownload != null && !MediaUrlResolver.isLocalMedia(music.url)
    val canUsePlatformFeatures = !music.isThirdParty
    val primaryArtist = music.artists.firstOrNull()
    val album = music.album

    FoxSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
        ) {
            // ── 歌曲头：44dp 封面 + 标题 + 艺人 chip ─────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CachedImage(
                    imageUrl = music.coverImage,
                    contentDescription = music.title,
                    modifier = Modifier.size(44.dp),
                    shape = MusicSheetCoverShape,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = music.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (primaryArtist != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        AlbumArtistChip(
                            name = primaryArtist.name,
                            avatarUrl = primaryArtist.avatar ?: primaryArtist.coverImage,
                            enabled = canUsePlatformFeatures,
                            onClick = {
                                if (canUsePlatformFeatures) {
                                    onDismiss()
                                    onArtistClick(primaryArtist.id)
                                }
                            },
                        )
                    }
                }
            }

            // ── 双主钮行 ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MusicSheetPrimaryButton(
                    text = "下一首播放",
                    icon = Icons.Default.SkipNext,
                    onClick = {
                        onPlayNext()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                if (canUsePlatformFeatures) {
                    MusicSheetGhostButton(
                        text = "添加到歌单",
                        icon = Icons.Default.PlaylistAdd,
                        onClick = onAddToPlaylist,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── 常规操作 ────────────────────────────────────────────────
            if (showDownload) {
                MusicSheetActionRow(
                    icon = Icons.Default.CloudDownload,
                    label = "下载歌曲",
                    trailing = "标准音质",
                    onClick = onDownload ?: {},
                )
            }
            if (canUsePlatformFeatures && onShare != null) {
                MusicSheetActionRow(
                    icon = Icons.Default.Share,
                    label = "分享给好友",
                    onClick = {
                        onShare()
                        onDismiss()
                    },
                )
            }
            if (canUsePlatformFeatures && (primaryArtist != null || album != null)) {
                MusicSheetActionRow(
                    icon = Icons.Default.Album,
                    label = "查看专辑与艺人",
                    trailing = album?.title ?: primaryArtist?.name,
                    onClick = {
                        val artistId = primaryArtist?.id
                        if (artistId != null) {
                            onDismiss()
                            onArtistClick(artistId)
                        }
                    },
                )
            }

            // ── 反馈次级区：发丝线隔开，icon 不套容器，文字 ink3 ──────────
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                thickness = 1.dp,
                color = foxDialogHairline(),
            )
            MusicSheetSecondaryRow(
                icon = Icons.Default.Flag,
                label = "举报",
                onClick = onReport,
            )
            MusicSheetSecondaryRow(
                icon = Icons.Default.Warning,
                label = "反馈音乐不匹配",
                onClick = onFeedbackMismatch,
            )
            if (isAdmin && onCorrectSource != null) {
                MusicSheetSecondaryRow(
                    icon = Icons.Default.Build,
                    label = "音源纠错",
                    onClick = onCorrectSource,
                )
            }
        }
    }
}

/** 歌曲头里的艺人胶囊：18dp 圆头像 + 11sp 名字，variant 底。 */
@Composable
private fun AlbumArtistChip(
    name: String,
    avatarUrl: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = avatarUrl,
            contentDescription = name,
            modifier = Modifier.size(18.dp),
            shape = CircleShape,
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** primary 胶囊主钮（抖音式「下一首播放」高亮）。 */
@Composable
private fun MusicSheetPrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MusicSheetCapsuleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** ghost 胶囊次钮（hairline 描边 + 纸面底）。 */
@Composable
private fun MusicSheetGhostButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MusicSheetCapsuleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = foxDialogHairline(),
                shape = MusicSheetCapsuleShape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** 常规操作行：52dp 行高 + 32dp 圆角 10 icon 容器（底 surfaceVariant）+ 可选右侧元信息。 */
@Composable
private fun MusicSheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(MusicSheetIconShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 反馈次级行：icon 不套容器（直接弱化色图标），文字 ink3。 */
@Composable
private fun MusicSheetSecondaryRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
