package com.fox.music.core.model.chat

import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.user.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: Long,
    val localId: String? = null,
    @SerialName("sender_id")
    val senderId: Long,
    @SerialName("receiver_id")
    val receiverId: Long,
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    @SerialName("is_recalled")
    val isRecalled: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("read_at")
    val readAt: String? = null,
    val errorMessage: String? = null,
    val localMediaUri: String? = null,
    val localMediaFileName: String? = null,
    val remoteMediaUrl: String? = null,
    val fileType: String? = null,
    val uploadedAt: Long? = null,
    val audioDurationMs: Long? = null,
    @SerialName("share_type")
    val shareType: String? = null,
    @SerialName("share_id")
    val shareId: Long? = null,
    @SerialName("share_data")
    val shareData: ShareData? = null,
)

@Serializable
enum class MessageType {
    @SerialName("text")
    TEXT,
    @SerialName("image")
    IMAGE,
    @SerialName("audio")
    AUDIO,
    @SerialName("file")
    FILE,
    @SerialName("music")
    MUSIC,
    @SerialName("share")
    SHARE
}

@Serializable
enum class MessageStatus {
    @SerialName("sending")
    SENDING,
    @SerialName("sent")
    SENT,
    @SerialName("delivered")
    DELIVERED,
    @SerialName("read")
    READ,
    @SerialName("failed")
    FAILED
}

@Serializable
data class ChatConversation(
    val id: Long,
    val user: User,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val isPinned: Boolean = false,
)

data class SearchResultItem(
    val user: User,
    val lastMessage: Message,
    val matchCount: Int,
)

/**
 * 分享消息的附加数据。
 * 结构随 [shareType] 变化（music/playlist/artist/album），采用宽泛兼容策略：
 * 所有可能字段集中在一个 data class 中，缺失字段为 null/默认值。
 * 数据被删除时 shareData 为 null。
 */
@Serializable
data class ShareData(
    val id: Long = 0,
    val title: String? = null,
    val name: String? = null,
    @SerialName("cover_image")
    val coverImage: String? = null,
    val avatar: String? = null,
    val description: String? = null,
    val url: String? = null,
    val duration: Long = 0,
    @SerialName("track_count")
    val trackCount: Int = 0,
    @SerialName("play_count")
    val playCount: Int = 0,
    @SerialName("favorite_count")
    val favoriteCount: Int = 0,
    val artists: List<Artist> = emptyList(),
)
