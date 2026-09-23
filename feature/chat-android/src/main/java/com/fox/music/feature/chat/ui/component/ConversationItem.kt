package com.fox.music.feature.chat.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.fox.music.core.data.mapper.previewForMessage
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageStatus
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.feature.chat.util.displayName
import com.fox.music.feature.chat.util.formatMessageDate

@Composable
fun ConversationItem(
    conversation: ChatConversation,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var menuHeightPx by remember { mutableIntStateOf(0) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(showMenu) {
        if (showMenu) {
            kotlinx.coroutines.delay(3000)
            showMenu = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (conversation.isPinned)
                    MaterialTheme.colorScheme.surfaceContainerHigh
                else
                    Color.Transparent
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (showMenu) {
                            showMenu = false
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CachedImage(
                imageUrl = conversation.user.avatar,
                contentDescription = conversation.user.displayName(),
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                placeholderIcon = Icons.Default.Person,
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.user.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatMessageDate(
                            conversation.updatedAt ?: conversation.lastMessage?.createdAt,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = previewForConversation(conversation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = previewColor(conversation),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (conversation.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        UnreadBadge(count = conversation.unreadCount)
                    }
                }
            }
        }

        if (showMenu) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -menuHeightPx),
                onDismissRequest = { showMenu = false },
            ) {
                ConversationActionMenu(
                    isPinned = conversation.isPinned,
                    onDeleteClick = onDeleteClick,
                    onPinClick = onPinClick,
                    onDismiss = { showMenu = false },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        menuHeightPx = coords.size.height
                    }
                )
            }
        }
    }
}

private fun previewForConversation(conversation: ChatConversation): String {
    val lastMessage = conversation.lastMessage
    return when {
        lastMessage?.isRecalled == true -> "[消息已撤回]"
        lastMessage?.status == MessageStatus.FAILED -> "[发送失败] ${previewForType(lastMessage)}"
        lastMessage?.status == MessageStatus.SENDING -> "[发送中] ${previewForType(lastMessage)}"
        lastMessage != null -> previewForType(lastMessage)
        else -> "暂无消息"
    }
}

@Composable
private fun previewColor(conversation: ChatConversation) = when (conversation.lastMessage?.status) {
    MessageStatus.FAILED -> MaterialTheme.colorScheme.error
    MessageStatus.SENDING -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun previewForType(message: Message): String = previewForMessage(message)

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError,
        )
    }
}
