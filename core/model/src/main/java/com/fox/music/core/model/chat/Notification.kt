package com.fox.music.core.model.chat

import com.fox.music.core.model.user.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val content: String,
    @SerialName("is_read")
    val isRead: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    val sender: User? = null,
    val senderId: Long? = null,
    val targetId: Long? = null,
    val targetType: String? = null
)

@Serializable
enum class NotificationType {
    @SerialName("system")
    SYSTEM,
    @SerialName("friend_request")
    FRIEND_REQUEST,
    @SerialName("comment")
    COMMENT,
    @SerialName("like")
    LIKE,
    @SerialName("follow")
    FOLLOW,
    @SerialName("mention")
    MENTION,
    @SerialName("music")
    MUSIC,
    /** 私信/聊天消息通知，仅用于本地会话列表，不在通知分类页展示 */
    @SerialName("message")
    MESSAGE,
}
