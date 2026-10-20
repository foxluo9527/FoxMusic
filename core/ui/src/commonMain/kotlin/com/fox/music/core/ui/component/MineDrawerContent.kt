package com.fox.music.core.ui.component

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.ui.theme.FoxDisplayFont

/**
 * 全局左侧抽屉内容（仿网易云侧边栏）。
 *
 * 纯 UI，回调驱动，Android 与 iOS 各自负责宿主与导航。顶部为个人资料入口，
 * 中部为常用功能入口，底部仅一个「设置」按钮。
 */
@Composable
fun MineDrawerContent(
    isLogin: Boolean,
    nickname: String?,
    avatarUrl: String?,
    unreadCount: Int,
    onProfileClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onAssistantClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onThemeClick: () -> Unit,
    onManageLibraryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLoginClick: () -> Unit = {},
    /** 用户主页背景图（封面）；为空时头部用主题渐变兜底。 */
    coverUrl: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val displayName = if (isLogin) nickname?.takeIf { it.isNotBlank() } ?: "FoxMusic" else "点击登录"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        DrawerProfileHeader(
            isLogin = isLogin,
            displayName = displayName,
            avatarUrl = avatarUrl,
            coverUrl = coverUrl,
            onClick = { if (isLogin) onProfileClick() else onLoginClick() },
        )

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(20.dp))

        DrawerMenuItem(
            icon = Icons.AutoMirrored.Filled.Chat,
            label = "我的消息",
            badgeCount = unreadCount,
            onClick = onMessagesClick,
        )
        DrawerMenuItem(
            icon = Icons.Filled.AutoAwesome,
            label = "AI 音乐助手",
            onClick = onAssistantClick,
        )
        DrawerMenuItem(
            icon = Icons.Filled.Edit,
            label = "资料编辑",
            onClick = onEditProfileClick,
        )
        DrawerMenuItem(
            icon = Icons.Filled.Timer,
            label = "定时关闭",
            onClick = onSleepTimerClick,
        )
        DrawerMenuItem(
            icon = Icons.Filled.Download,
            label = "下载管理",
            onClick = onDownloadsClick,
        )
        DrawerMenuItem(
            icon = Icons.Filled.Palette,
            label = "主题设置",
            onClick = onThemeClick,
        )
        DrawerMenuItem(
            icon = Icons.Filled.LibraryMusic,
            label = "曲库管理",
            onClick = onManageLibraryClick,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 底部唯一「设置」按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable(onClick = onSettingsClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = scheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "设置",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 抽屉头部：用户主页封面 + 头像 + 昵称。
 *
 * 头部高度自带状态栏区域（面板本身上移到屏幕顶端），封面图直接铺到状态栏下方，
 * 因此不再出现「状态栏一条灰底」的断层；封面为空时用主题渐变兜底。
 */
@Composable
private fun DrawerProfileHeader(
    isLogin: Boolean,
    displayName: String,
    avatarUrl: String?,
    coverUrl: String?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable(onClick = onClick),
    ) {
        if (coverUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                scheme.primary.copy(alpha = 0.9f),
                                scheme.primary.copy(alpha = 0.35f),
                                scheme.background,
                            ),
                        ),
                    ),
            )
        } else {
            CachedImage(
                imageUrl = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
            )
            // 压暗封面，保证白色文字与头像描边可读。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x66000000),
                                Color(0x33000000),
                                Color(0xCC000000),
                            ),
                        ),
                    ),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CachedImage(
                imageUrl = avatarUrl,
                contentDescription = displayName,
                modifier = Modifier
                    .size(64.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .clip(CircleShape),
                shape = CircleShape,
                placeholderIcon = Icons.Filled.Person,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FoxDisplayFont,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isLogin) "查看个人主页" else "登录后同步收藏与歌单",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    badgeCount: Int = 0,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (badgeCount > 0) {
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(scheme.error)
                    .padding(horizontal = 7.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = scheme.onError,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
