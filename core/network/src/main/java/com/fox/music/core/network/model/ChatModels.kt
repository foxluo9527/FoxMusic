package com.fox.music.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: Long,
    @SerialName("sender_id")
    val senderId: Long,
    @SerialName("receiver_id")
    val receiverId: Long,
    val content: String,
    val type: String = "text",
    /** 部分接口返回；未读消息接口使用 is_read */
    val status: String? = null,
    @SerialName("is_recalled")
    val isRecalled: Boolean = false,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("read_at")
    val readAt: String? = null,
    @SerialName("is_read")
    val isRead: Boolean = false,
    @SerialName("is_deleted")
    val isDeleted: Boolean = false,
    @SerialName("sent_at")
    val sentAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("is_notified")
    val isNotified: Int = 0,
    @SerialName("sender_nickname")
    val senderNickname: String? = null,
    @SerialName("sender_avatar")
    val senderAvatar: String? = null,
    @SerialName("sender_remark")
    val senderRemark: String? = null,
    @SerialName("file_url")
    val fileUrl: String? = null,
    @SerialName("file_type")
    val fileType: String? = null,
    @SerialName("file_name")
    val fileName: String? = null,
    @SerialName("file_size")
    val fileSize: Long? = null,
    @SerialName("voice_url")
    val voiceUrl: String? = null,
    @SerialName("voice_duration")
    val voiceDuration: Long? = null,
    @SerialName("share_type")
    val shareType: String? = null,
    @SerialName("share_id")
    val shareId: Long? = null,
    @SerialName("share_data")
    val shareData: ShareDataDto? = null,
)

@Serializable
data class ConversationDto(
    val id: Long,
    val user: UserDto,
    val lastMessage: MessageDto? = null,
    val unreadCount: Int = 0,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class SendMessageRequest(
    val receiverId: Long,
    val content: String,
    val type: String = "text",
    @SerialName("file_url")
    val fileUrl: String? = null,
    @SerialName("file_type")
    val fileType: String? = null,
    @SerialName("file_name")
    val fileName: String? = null,
    @SerialName("file_size")
    val fileSize: String? = null,
)

@Serializable
data class ShareMessageRequest(
    val receiverId: Long,
    val shareType: String,
    val shareId: Long,
    val content: String = "",
)

@Serializable
data class MarkChatReadRequest(
    val targetId: Long
)

/**
 * 分享消息的服务端返回数据，结构随 shareType 变化。
 * 采用宽泛兼容策略：所有可能字段集中定义，缺失字段为 null/默认值。
 */
@Serializable
data class ShareDataDto(
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
    val lyrics: String? = null,
    @SerialName("lyrics_trans")
    val lyricsTrans: String? = null,
    @SerialName("lyrics_url")
    val lyricsUrl: String? = null,
    val artists: List<ShareArtistDto> = emptyList(),
)

@Serializable
data class ShareArtistDto(
    val id: Long = 0,
    val name: String = "",
    val avatar: String? = null,
    @SerialName("cover_image")
    val coverImage: String? = null,
)
