package com.fox.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.fox.music.core.common.EventViewModel
import com.fox.music.core.common.realtime.RealtimeNotificationEvent
import com.fox.music.core.model.chat.NotificationType
import com.fox.music.feature.chat.chatDetailRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InAppNotificationHost(
    navController: NavHostController,
) {
    var currentEvent by remember { mutableStateOf<RealtimeNotificationEvent?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        EventViewModel.inAppNotificationEvents.collect { event ->
            currentEvent = event
            delay(4_000)
            if (currentEvent?.id == event.id) {
                currentEvent = null
            }
        }
    }

    LaunchedEffect(Unit) {
        EventViewModel.pendingNavigation.collect { request ->
            navController.navigate(request.route) {
                launchSingleTop = true
            }
        }
    }

    val event = currentEvent
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = event != null,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
        ) {
            if (event != null) {
                InAppNotificationBanner(
                    event = event,
                    onClick = {
                        currentEvent = null
                        val route = event.peerUserId
                            ?.takeIf { it > 0 }
                            ?.let { chatDetailRoute(it) }
                            ?: event.route
                        scope.launch {
                            EventViewModel.requestNavigation(route)
                        }
                    },
                    onDismiss = { currentEvent = null },
                )
            }
        }
    }
}

@Composable
private fun InAppNotificationBanner(
    event: RealtimeNotificationEvent,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.inverseSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = event.title.ifBlank { defaultTitle(event.type) },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = event.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.85f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun defaultTitle(type: NotificationType): String = when (type) {
    NotificationType.COMMENT -> "新评论提醒"
    NotificationType.LIKE -> "点赞提醒"
    NotificationType.FOLLOW -> "关注提醒"
    NotificationType.MENTION -> "提及提醒"
    NotificationType.MESSAGE -> "聊天消息"
    NotificationType.FRIEND_REQUEST -> "好友请求"
    NotificationType.MUSIC -> "音乐提醒"
    NotificationType.SYSTEM -> "系统通知"
}
