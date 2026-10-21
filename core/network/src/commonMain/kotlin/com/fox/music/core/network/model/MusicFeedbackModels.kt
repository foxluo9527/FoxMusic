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

/**
 * 我的音源反馈记录（GET /api/music/feedback/mine）。
 *
 * 字段全部可空/带默认值：服务端由 LEFT JOIN 组装，歌曲被删或字段缺失时
 * 单条记录不应导致整页解析失败。
 */
@Serializable
data class MyMusicFeedbackDto(
    val id: Long = 0L,
    @SerialName("music_id")
    val musicId: Long = 0L,
    val content: String? = null,
    /** pending / resolved / rejected */
    val status: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("handled_at")
    val handledAt: String? = null,
    @SerialName("handle_note")
    val handleNote: String? = null,
    /** running / succeeded / failed / skipped */
    @SerialName("auto_repair_status")
    val autoRepairStatus: String? = null,
    /** ai / legacy */
    @SerialName("auto_repair_via")
    val autoRepairVia: String? = null,
    @SerialName("auto_repair_detail")
    val autoRepairDetail: String? = null,
    @SerialName("auto_repair_at")
    val autoRepairAt: String? = null,
    @SerialName("music_title")
    val musicTitle: String? = null,
    @SerialName("music_artists")
    val musicArtists: String? = null,
    @SerialName("music_duration")
    val musicDuration: Long = 0L,
    @SerialName("music_cover")
    val musicCover: String? = null,
)
