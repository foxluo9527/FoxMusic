package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result

interface ImportRepository {

    suspend fun parsePlaylist(url: String): Result<ParsedPlaylist>

    suspend fun getScrapeConfig(): Result<ScrapeConfig>

    /** 服务端导入链路解析播放直链（客户端无法访问音源站时兜底） */
    suspend fun resolveAudio(title: String, artist: String): Result<ResolvedAudio>

    suspend fun lookupSourceLink(title: String, artist: String, source: String = "hifinicn"): Result<SourceLink?>

    suspend fun upsertSourceLink(
        title: String,
        artist: String,
        detailUrl: String,
        matchScore: Int,
        titleRaw: String? = null,
        source: String = "hifinicn",
        origin: String = "playlist-import",
    ): Result<Unit>

    suspend fun reportUnreliableLink(
        title: String,
        artist: String,
        detailUrl: String,
        titleRaw: String? = null,
        source: String = "hifinicn",
    ): Result<Unit>

    suspend fun fetchLyrics(platform: String, externalId: String): Result<ImportedLyrics>

    suspend fun findMusic(title: String, artist: String): Result<Long?>

    suspend fun findOrCreateMusic(
        title: String,
        url: String,
        coverImage: String?,
        durationMs: Long,
        artists: List<String>,
        lyrics: String = "",
        lyricsTrans: String = "",
        albumName: String? = null,
        albumCover: String? = null,
        trackNumber: Int = 1,
        platform: String? = null,
        externalId: String? = null,
    ): Result<Long>

    suspend fun updateImportSession(
        playlistId: Long,
        totalTracks: Int?,
        importedTracks: Int?,
        status: String?,
        error: String? = null,
    ): Result<Unit>

    suspend fun importMusic(url: String, platform: String? = "mobile"): Result<ImportMusicResponse>

    suspend fun importNeteaseCharts(platform: String? = "mobile"): Result<ImportChartsResult>

    /** 启动热门歌单同步任务，成功时返回服务端 message */
    suspend fun importHotPlaylists(platform: String? = "mobile"): Result<String>
}

data class ParsedPlaylist(
    val platform: String,
    val sourceId: String,
    val title: String,
    val cover: String?,
    val description: String,
    val tracks: List<ParsedTrack>,
)

data class ParsedTrack(
    val externalId: String?,
    val title: String,
    val artists: List<String>,
    val durationMs: Long,
    val cover: String?,
    val albumName: String? = null,
    val albumCover: String? = null,
    val trackNumber: Int,
)

data class ScrapeConfig(
    val userAgent: String,
    val preferredSource: String,
    val importSiteOrder: List<String> = listOf("hifinicn", "hifiti", "hifini", "hifiii"),
    val sites: Map<String, ScrapeSite>,
)

data class ScrapeSite(
    val cookie: String,
    val baseUrl: String,
    val audioDownloadReferer: String?,
    val searchPath: String? = null,
    val resultLinkSelector: String? = null,
    val strategies: List<ScrapeStrategy> = emptyList(),
)

data class ScrapeStrategy(
    val key: String,
    val searchPath: String? = null,
    val searchKeywordFormat: String = "{songName}",
    val maxPage: Int = 1,
    val resultLinkSelector: String? = null,
    val requireQualityTag: Boolean = true,
    val requireArtistInTitle: Boolean = false,
)

data class ResolvedAudio(
    val url: String,
    val source: String?,
    val referer: String?,
    val score: Double? = null,
    val exact: Boolean = false,
)

data class SourceLink(
    val detailUrl: String,
    val matchScore: Int,
    val titleRaw: String? = null,
)

data class ImportedLyrics(
    val lyric: String = "",
    val lyricTrans: String = "",
)

data class ImportMusicResponse(
    val albumId: Long? = null,
    val taskId: String? = null,
    val isImporting: Boolean = false,
)

data class ImportChartsResult(
    val total: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
)
