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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.ui.theme.InkGlass

/**
 * 全局左侧抽屉（原型 07）。
 *
 * 结构自上而下：深色头部（光晕 + 头像 + 昵称 + 主页入口）→ 两张快捷卡（消息带未读徽标 /
 * AI 音乐助手）→「账号与内容」分组 → 设置分组 → 版本脚注。
 * 旧版是「大图横幅 + 8 个等距菜单行」，信息没有主次，也没有分组。
 *
 * 纯 UI，回调驱动，Android 与 iOS 各自负责宿主与导航。
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
    /** 用户主页背景图（封面）；深色头部不再铺封面图，仅作为头像兜底。 */
    coverUrl: String? = null,
    /** 版本脚注文案，如 "FOXMUSIC 4.2.0"；为空时不展示。 */
    versionLabel: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val displayName = if (isLogin) nickname?.takeIf { it.isNotBlank() } ?: "FoxMusic" else "点击登录"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        DrawerHeader(
            isLogin = isLogin,
            displayName = displayName,
            avatarUrl = avatarUrl,
            onClick = { if (isLogin) onProfileClick() else onLoginClick() },
        )

        // 中段可滚动（快捷卡 + 分组）；脚注贴在面板底部，不随内容漂
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
        // ── 快捷卡：消息（未读徽标）+ AI 助手 ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DrawerQuickCard(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = "我的消息",
                sub = "评论 · 系统",
                badgeCount = unreadCount,
                onClick = onMessagesClick,
                modifier = Modifier.weight(1f),
            )
            DrawerQuickCard(
                icon = Icons.Filled.AutoAwesome,
                iconBoxBackground = Color(0xFF1B1712),
                iconTint = Color(0xFFE2563C),
                label = "AI 音乐助手",
                sub = "一句话建歌单",
                onClick = onAssistantClick,
                modifier = Modifier.weight(1f),
            )
        }

        // ── 分组：账号与内容 ─────────────────────────────────────────────
        DrawerGroupLabel(
            text = "账号与内容",
            modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 4.dp),
        )
        DrawerRow(icon = Icons.Filled.Edit, label = "资料编辑", onClick = onEditProfileClick)
        DrawerRow(icon = Icons.Filled.Palette, label = "主题设置", onClick = onThemeClick)
        DrawerRow(icon = Icons.Filled.Timer, label = "定时关闭", onClick = onSleepTimerClick)
        DrawerRow(icon = Icons.Filled.Download, label = "下载管理", onClick = onDownloadsClick)
        DrawerRow(icon = Icons.Filled.LibraryMusic, label = "曲库管理", onClick = onManageLibraryClick)

        // ── 分组：设置 ──────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(10.dp))
        DrawerRow(
            icon = Icons.Filled.Settings,
            label = "设置",
            onClick = onSettingsClick,
            selected = true,
        )

        Spacer(modifier = Modifier.height(12.dp))
        }

        // ── 版本脚注：贴底 + 顶部 hairline（设计稿 .dw-foot）────────────────
        if (!versionLabel.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.onSurface.copy(alpha = 0.05f))
                    .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = versionLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.6.sp,
                    color = scheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}

/** 深色头部：#17140F 底 + 右上暖橙光晕 + 头像 44 / 昵称 16 / 「查看个人主页」。 */
@Composable
private fun DrawerHeader(
    isLogin: Boolean,
    displayName: String,
    avatarUrl: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(InkGlass)
            .clickable(onClick = onClick),
    ) {
        // 光晕：右上角一团 accent 辉光，给纯深色块一点温度
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = 66.dp, y = (-52).dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x73E2563C), Color.Transparent),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CachedImage(
                imageUrl = avatarUrl,
                contentDescription = displayName,
                modifier = Modifier
                    .size(44.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .clip(CircleShape),
                shape = CircleShape,
                placeholderIcon = Icons.Filled.Person,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFBF8F4),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isLogin) "查看个人主页" else "登录后同步收藏与歌单",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isLogin) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** 快捷卡：白底 13dp 圆角 + 26dp 图标块 + 标签 + 副文案，右上角可挂未读徽标。 */
@Composable
private fun DrawerQuickCard(
    icon: ImageVector,
    label: String,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    iconBoxBackground: Color = Color(0xFFEFE9DF),
    iconTint: Color = Color(0xFF1A1512),
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 13.dp, bottom = 12.dp),
    ) {
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(Color(0xFFB8513A))
                    // 单位数：16dp 正圆（文字天然居中）；多位数/99+：高度仍 16dp，
                    // 宽度随内容撑开成胶囊，保证圆点观感不破。
                    .then(
                        if (badgeCount < 10) {
                            Modifier.size(16.dp)
                        } else {
                            Modifier.height(16.dp).padding(horizontal = 5.dp)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = Color.White,
                    fontSize = 9.5.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Column {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBoxBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sub,
                fontSize = 9.5.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DrawerGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.6.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
    )
}

/**
 * 分组行：46dp 高 + 19dp 线性图标 + 13.5sp 标签 + 右侧 chevron。
 * [selected] 仅用于「设置」这类当前所在页：白底 + 发丝描边。
 */
@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) scheme.surface else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = scheme.onSurface.copy(alpha = if (selected) 0.05f else 0f),
                shape = RoundedCornerShape(11.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.size(19.dp),
        )
        Spacer(modifier = Modifier.width(13.dp))
        Text(
            text = label,
            fontSize = 13.5.sp,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurface.copy(alpha = 0.26f),
            modifier = Modifier.size(14.dp),
        )
    }
}
