package com.fox.music.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ParsePlaylistRequest(
    val url: String,
)

@Serializable
data class ParsedPlaylistDto(
    val platform: String = "",
    val type: String = "playlist",
    @SerialName("sourceId") val sourceId: String = "",
    val title: String = "",
    val cover: String? = null,
    val description: String = "",
    @SerialName("trackCount") val trackCount: Int = 0,
    val tracks: List<ParsedTrackDto> = emptyList(),
)

@Serializable
data class ParsedTrackDto(
    @SerialName("externalId") val externalId: String? = null,
    val title: String = "",
    val artists: List<ParsedArtistDto> = emptyList(),
    val duration: Long = 0,
    val cover: String? = null,
    @SerialName("trackNumber") val trackNumber: Int = 0,
    @SerialName("albumName") val albumName: String? = null,
    @SerialName("albumCover") val albumCover: String? = null,
)

@Serializable
data class ParsedArtistDto(
    val id: String? = null,
    val name: String = "",
)

@Serializable
data class ScrapeConfigDto(
    @SerialName("userAgent") val userAgent: String = "",
    val sites: Map<String, ScrapeSiteDto> = emptyMap(),
    @SerialName("preferredSource") val preferredSource: String = "hifinicn",
    @SerialName("importSiteOrder") val importSiteOrder: List<String> = emptyList(),
)

@Serializable
data class ScrapeSiteDto(
    val cookie: String = "",
    @SerialName("baseUrl") val baseUrl: String = "",
    val hosts: List<String> = emptyList(),
    @SerialName("audioDownloadReferer") val audioDownloadReferer: String? = null,
    @SerialName("searchPath") val searchPath: String? = null,
    @SerialName("resultLinkSelector") val resultLinkSelector: String? = null,
    val strategies: List<ScrapeStrategyDto> = emptyList(),
)

@Serializable
data class ScrapeStrategyDto(
    val key: String = "",
    @SerialName("searchPath") val searchPath: String? = null,
    @SerialName("searchKeywordFormat") val searchKeywordFormat: String = "{songName}",
    @SerialName("maxPage") val maxPage: Int = 1,
    @SerialName("resultLinkSelector") val resultLinkSelector: String? = null,
    @SerialName("requireQualityTag") val requireQualityTag: Boolean = true,
    @SerialName("requireArtistInTitle") val requireArtistInTitle: Boolean = false,
)

@Serializable
data class ResolveAudioRequest(
    val title: String,
    val artist: String,
    /** 原曲时长（毫秒）：服务端 API 源据此交叉校验，显著降低同名错版本命中 */
    @SerialName("durationMs") val durationMs: Long? = null,
)

@Serializable
data class ResolveAudioDto(
    val url: String = "",
    val source: String? = null,
    val referer: String? = null,
    val score: Double? = null,
    val exact: Boolean = false,
    @SerialName("durationMs") val durationMs: Long? = null,
    /** true=命中服务端 API 源（网易云/酷我/Hifini解析/插件源），false=回落 hifini 站点链路 */
    @SerialName("viaApiSources") val viaApiSources: Boolean = false,
)

@Serializable
data class SearchImportSourcesRequest(
    val title: String,
    val artist: String = "",
    @SerialName("durationMs") val durationMs: Long? = null,
    val limit: Int = 8,
)

@Serializable
data class ImportSourceCandidateDto(
    val url: String = "",
    val source: String? = null,
    val referer: String? = null,
    val score: Double? = null,
    val exact: Boolean = false,
    @SerialName("durationMs") val durationMs: Long? = null,
    /** 服务端按歌单原曲时长做的交叉判定；false 表示时长明显不符（试听片段/串烧/其它版本） */
    @SerialName("durationMatch") val durationMatch: Boolean = true,
    val title: String = "",
    val artist: String = "",
    @SerialName("detailUrl") val detailUrl: String? = null,
)

@Serializable
data class SearchImportSourcesDto(
    val candidates: List<ImportSourceCandidateDto> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class SourceLinkDto(
    val id: Long? = null,
    val title: String = "",
    val artist: String = "",
    val source: String = "hifinicn",
    @SerialName("detailUrl") val detailUrl: String = "",
    @SerialName("matchScore") val matchScore: Int = 0,
    @SerialName("titleRaw") val titleRaw: String? = null,
    @SerialName("verifiedAt") val verifiedAt: String? = null,
)

@Serializable
data class UpsertSourceLinkRequest(
    val title: String,
    val artist: String = "",
    val source: String = "hifinicn",
    val detailUrl: String,
    val matchScore: Int,
    val titleRaw: String? = null,
    val verified: Boolean = true,
    /** 记录来源（服务端白名单校验）：导入歌单传 playlist-import，其余走服务端默认 manual */
    val origin: String? = null,
)

@Serializable
data class FindMusicDto(
    @SerialName("musicId") val musicId: Long? = null,
    val exists: Boolean = false,
)

@Serializable
data class FindOrCreateMusicRequest(
    val title: String,
    val description: String = "",
    val url: String,
    @SerialName("cover_image") val coverImage: String? = null,
    val duration: Long = 0,
    val lyrics: String = "",
    @SerialName("lyrics_trans") val lyricsTrans: String = "",
    val artists: List<FindOrCreateArtistRequest> = emptyList(),
    val album: FindOrCreateAlbumRequest? = null,
    @SerialName("track_number") val trackNumber: Int = 1,
    val platform: String? = null,
    @SerialName("external_id") val externalId: String? = null,
)

@Serializable
data class FindOrCreateAlbumRequest(
    val title: String,
    @SerialName("cover_image") val coverImage: String? = null,
)

@Serializable
data class FindOrCreateArtistRequest(
    val name: String,
)

@Serializable
data class FindOrCreateMusicDto(
    @SerialName("musicId") val musicId: Long,
    val created: Boolean = false,
)

@Serializable
data class ImportSessionRequest(
    @SerialName("totalTracks") val totalTracks: Int? = null,
    @SerialName("importedTracks") val importedTracks: Int? = null,
    val status: String? = null,
    val error: String? = null,
)

@Serializable
data class ImportLyricsDto(
    val lyric: String = "",
    @SerialName("lyricTrans") val lyricTrans: String = "",
)
