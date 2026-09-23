package com.fox.music.iosshared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 音乐评论 DTO —— 形状与后端 `routes/client/music-comments.js` 的 SELECT 别名一一对应。
 *
 * 为什么定义在 iosShared 而不是 :core:network：Android 端评论有自己的一套模型
 * （core:model 的 Comment），本轮只服务 iOS 播放器，放这里可以零改动共享模块、
 * 不动 Android 的编译产物。等后续做 iOS 社区页时再考虑上收。
 *
 * 注意 `is_like`：后端在返回前已把 `COUNT(*)` 折成布尔（`comment.is_like = x > 0`），
 * 所以这里直接声明成 Boolean。
 */
@Serializable
internal data class MusicCommentDto(
    val id: Long,
    @SerialName("music_id") val musicId: Long? = null,
    @SerialName("user_id") val userId: Long? = null,
    val content: String = "",
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("reply_id") val replyId: Long? = null,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("user_nickname") val userNickname: String? = null,
    @SerialName("user_avatar") val userAvatar: String? = null,
    @SerialName("reply_count") val replyCount: Int = 0,
    @SerialName("is_like") val isLike: Boolean = false,
    val replies: List<MusicCommentDto> = emptyList(),
    @SerialName("reply_to") val replyTo: ReplyTargetDto? = null,
)

/** 被回复对象（后端 `reply_to`）：昵称 + 头像 + 原文。 */
@Serializable
internal data class ReplyTargetDto(
    val username: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    @SerialName("user_id") val userId: Long? = null,
    val content: String? = null,
)
