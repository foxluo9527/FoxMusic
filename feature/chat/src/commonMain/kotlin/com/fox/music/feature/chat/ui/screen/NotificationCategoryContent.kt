package com.fox.music.feature.chat.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.fox.music.core.model.chat.Notification
import com.fox.music.feature.chat.util.formatMessageDate
import com.fox.music.feature.chat.viewmodel.NotificationCategory
import com.fox.music.feature.chat.viewmodel.NotificationCategoryState
import com.fox.music.core.ui.component.FoxInfoDialog
import com.fox.music.core.ui.component.FoxTopBar

/**
 * 通知分类页（评论和@ / 赞和通知 / 系统公告）—— 跨端无状态实现。
 *
 * 系统公告点击弹原文（不跳转），其余分类点击交给宿主跳帖子/音乐评论。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCategoryContent(
    state: NotificationCategoryState,
    onBack: () -> Unit,
    onNotificationClick: (Notification) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedAnnouncement by remember { mutableStateOf<Notification?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            FoxTopBar(
                title = state.category.title,
                showBackButton = true,
                onBackClick = onBack,
            )
        },
    ) { padding ->
        if (state.isLoading && state.notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                val emptyText = when {
                    state.error != null -> state.error
                    state.category == NotificationCategory.SYSTEM -> "暂无系统公告"
                    else -> "没有新通知"
                }
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(state.notifications, key = { it.id }) { notification ->
                NotificationItem(
                    notification = notification,
                    onClick = {
                        if (state.category == NotificationCategory.SYSTEM) {
                            selectedAnnouncement = notification
                        }
                        onNotificationClick(notification)
                    },
                )
            }
        }
    }

    selectedAnnouncement?.let { announcement ->
        val createdAt = announcement.createdAt
        val dateLine = if (!createdAt.isNullOrBlank()) formatMessageDate(createdAt) else ""
        FoxInfoDialog(
            title = announcement.title,
            text = if (dateLine.isBlank()) {
                announcement.content
            } else {
                "$dateLine\n\n${announcement.content}"
            },
            confirmText = "知道了",
            onDismiss = { selectedAnnouncement = null },
        )
    }
}

@Composable
private fun NotificationItem(
    notification: Notification,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = if (notification.isRead) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatMessageDate(notification.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = notification.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
