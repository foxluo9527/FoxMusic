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

/** 音乐评论通知：type 与帖子评论同为 comment，靠 target_type 区分 */
fun Notification.isMusicCommentNotification(): Boolean =
    targetType?.equals("music_comment", ignoreCase = true) == true

/** 帖子评论通知：type=comment/mention 且 target_type 不是 music_comment */
fun Notification.isPostCommentNotification(): Boolean =
    (type == NotificationType.COMMENT || type == NotificationType.MENTION) &&
        !isMusicCommentNotification()

/** 解析帖子评论通知关联的帖子 ID（target_id 为帖子 ID） */
fun Notification.resolvePostId(): Long? {
    if (!isPostCommentNotification()) return null
    return targetId
}

/** 解析音乐评论通知关联的评论 ID */
fun Notification.resolveMusicCommentId(): Long? {
    if (!isMusicCommentNotification()) return null
    return targetId
}

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
