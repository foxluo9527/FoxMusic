package com.fox.music.core.model.music

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 第三方音源相关网络 DTO（放在 core:model 以便 core:domain 也能引用，
 * 避免 core:domain 反向依赖 core:network 形成循环）。
 */

/** 按需解析第三方单曲音源请求（/api/music/third-party/resolve） */
@Serializable
data class ResolveThirdPartyRequest(
    val platform: String? = null,
    val externalId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val provider: String? = null,
    @SerialName("durationMs")
    val durationMs: Long? = null,
    /** 渠道候选的解析方式：hifini_site（站点详情页逆向）/ musicFreePlugins（插件源）等 */
    val resolveProvider: String? = null,
    val resolveParams: CandidateResolveParams? = null,
)

/** 渠道候选的解析参数（勾选 RESOLVE 型候选时原样回传给服务端） */
@Serializable
data class CandidateResolveParams(
    val site: String? = null,
    val detailUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    @SerialName("durationMs")
    val durationMs: Long? = null,
)

/** 按需解析返回的可播放地址 */
@Serializable
data class ResolveThirdPartyDto(
    val url: String = "",
    val source: String = "",
)

/**
 * 刮削歌词（/api/music/lyrics/fetch，网易云官方优先 + LRCLIB/lyrics.ovh 兜底）。
 * 除了歌词本身，服务端还回「命中了哪首歌」——歌词纠错要靠它让用户确认版本。
 */
@Serializable
data class ScrapedLyricsDto(
    val lyric: String? = null,
    val plain: String? = null,
    val synced: String? = null,
    val instrumental: Boolean = false,
    /** 译文（网易云 tlyric，无则为 null） */
    val trans: String? = null,
    /** 命中的曲目名（可能与请求的 title 不同：版本/译名差异） */
    val matchedTitle: String? = null,
    val matchedArtist: String? = null,
    val album: String? = null,
    @SerialName("durationMs")
    val durationMs: Long? = null,
    /** 命中来源：netease / lrclib / lyricsovh */
    val source: String? = null,
    val sourceId: String? = null,
)

/** 平台是否已收录某首歌（/api/music/exists） */
@Serializable
data class MusicExistsDto(
    val exists: Boolean = false,
    val musicId: Long? = null,
)

/** 收录单曲到平台请求（/api/music/import-single） */
@Serializable
data class ImportSingleRequest(
    val platform: String? = null,
    val externalId: String? = null,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val coverUrl: String? = null,
    @SerialName("durationMs")
    val durationMs: Long? = null,
    val audioUrl: String? = null,
    /** 歌词（可选）：二级页预拉的刮削歌词或一级页平台歌词，缺了服务端按 歌名+艺人 自动补 */
    val lyrics: String? = null,
    @SerialName("lyricsTrans")
    val lyricsTrans: String? = null,
    val playlistId: Long? = null,
)

/** 收录单曲返回结果 */
@Serializable
data class ImportSingleResponseDto(
    val musicId: Long = 0,
    val isNew: Boolean = false,
    val playlistId: Long? = null,
    val alreadyInPlaylist: Boolean = false,
)

/**
 * 渠道候选（/api/music/third-party/provider-candidates）。
 * playType=DIRECT 官方 API 已带播放直链；RESOLVE 需勾选后逆向解析。
 */
@Serializable
data class ProviderCandidateDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    @SerialName("durationMs")
    val durationMs: Long? = null,
    val playType: String = "RESOLVE",
    val url: String? = null,
    val referer: String? = null,
    val score: Double? = null,
    val exact: Boolean = false,
    val resolveProvider: String? = null,
    val resolveParams: CandidateResolveParams? = null,
)

/** 一个音源渠道的候选分组（一个 tab） */
@Serializable
data class ProviderCandidateGroupDto(
    val key: String = "",
    val label: String = "",
    val playType: String = "RESOLVE",
    val items: List<ProviderCandidateDto> = emptyList(),
)
