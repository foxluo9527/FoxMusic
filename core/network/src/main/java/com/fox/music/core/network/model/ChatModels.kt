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

data class MarkChatReadRequest(

    val targetId: Long

)


