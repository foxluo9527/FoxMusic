package com.fox.music.feature.chat.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.data.mapper.previewForMessage
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.MessageStatus
import com.fox.music.feature.chat.util.displayName
import com.fox.music.feature.chat.util.formatMessageDate

@Composable
fun ConversationItem(
    conversation: ChatConversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastMessage = conversation.lastMessage
    val lastMessageText = when {
        lastMessage?.isRecalled == true -> "[消息已撤回]"
        lastMessage?.status == MessageStatus.FAILED -> "[发送失败] ${previewForType(lastMessage)}"
        lastMessage?.status == MessageStatus.SENDING -> "[发送中] ${previewForType(lastMessage)}"
        lastMessage != null -> previewForType(lastMessage)
        else -> "暂无消息"
    }
    val previewColor = when (lastMessage?.status) {
        MessageStatus.FAILED -> MaterialTheme.colorScheme.error
        MessageStatus.SENDING -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                    text = lastMessageText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = previewColor,
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
