package com.fox.music.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 提交「音乐不匹配」反馈 */
@Serializable
data class SubmitMusicFeedbackRequest(
    @SerialName("music_id") val musicId: Long,
    val content: String? = null,
)

@Serializable
data class SubmitMusicFeedbackDto(
    val id: Long = 0,
)

/** 替换音乐音源（管理员） */
@Serializable
data class ReplaceMusicSourceRequest(
    val url: String,
    val referer: String? = null,
    val duration: Long? = null,
)
