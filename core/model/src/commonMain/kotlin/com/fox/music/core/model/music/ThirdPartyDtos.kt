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
