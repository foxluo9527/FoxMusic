package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.domain.repository.ImportChartsResult
import com.fox.music.core.domain.repository.ImportMusicResponse
import com.fox.music.core.domain.repository.ImportRepository
import com.fox.music.core.domain.repository.ImportedLyrics
import com.fox.music.core.domain.repository.ParsedPlaylist
import com.fox.music.core.domain.repository.ParsedTrack
import com.fox.music.core.domain.repository.ResolvedAudio
import com.fox.music.core.domain.repository.ScrapeConfig
import com.fox.music.core.domain.repository.ScrapeSite
import com.fox.music.core.domain.repository.ScrapeStrategy
import com.fox.music.core.domain.repository.SourceLink
import com.fox.music.core.network.api.ImportApiService
import com.fox.music.core.network.api.MusicApiService
import com.fox.music.core.network.api.PlaylistApiService
import com.fox.music.core.network.api.SourceLinkApiService
import com.fox.music.core.network.model.FindOrCreateAlbumRequest
import com.fox.music.core.network.model.FindOrCreateArtistRequest
import com.fox.music.core.network.model.FindOrCreateMusicRequest
import com.fox.music.core.network.model.ImportChartsRequest
import com.fox.music.core.network.model.ImportMusicRequest
import com.fox.music.core.network.model.ImportSessionRequest
import com.fox.music.core.network.model.ParsePlaylistRequest
import com.fox.music.core.network.model.ResolveAudioRequest
import com.fox.music.core.network.model.UpsertSourceLinkRequest
import javax.inject.Inject

class ImportRepositoryImpl @Inject constructor(
    private val importApiService: ImportApiService,
    private val sourceLinkApiService: SourceLinkApiService,
    private val musicApiService: MusicApiService,
    private val playlistApiService: PlaylistApiService,
) : ImportRepository {

    override suspend fun parsePlaylist(url: String): Result<ParsedPlaylist> = suspendRunCatching {
        val response = importApiService.parsePlaylist(ParsePlaylistRequest(url))
        val data = response.data ?: throw IllegalStateException(response.message.ifBlank { "解析失败" })
        if (!response.isSuccess) throw IllegalStateException(response.message)
        ParsedPlaylist(
            platform = data.platform,
            sourceId = data.sourceId,
            title = data.title,
            cover = data.cover,
            description = data.description,
            tracks = data.tracks.map { t ->
                ParsedTrack(
                    externalId = t.externalId,
                    title = t.title,
                    artists = t.artists.map { it.name }.filter { it.isNotBlank() },
                    durationMs = t.duration,
                    cover = t.cover,
                    albumName = t.albumName,
                    albumCover = t.albumCover,
                    trackNumber = t.trackNumber,
                )
            },
        )
    }

    override suspend fun getScrapeConfig(): Result<ScrapeConfig> = suspendRunCatching {
        val response = importApiService.getScrapeConfig()
        val data = response.data ?: throw IllegalStateException(response.message.ifBlank { "获取配置失败" })
        if (!response.isSuccess) throw IllegalStateException(response.message)
        ScrapeConfig(
            userAgent = data.userAgent,
            preferredSource = data.preferredSource,
            importSiteOrder = data.importSiteOrder.ifEmpty {
                listOf("hifinicn", "hifiti", "hifini", "hifiii")
            },
            sites = data.sites.mapValues { (_, s) ->
                ScrapeSite(
                    cookie = s.cookie,
                    baseUrl = s.baseUrl,
                    audioDownloadReferer = s.audioDownloadReferer,
                    searchPath = s.searchPath,
                    resultLinkSelector = s.resultLinkSelector,
                    strategies = s.strategies.map { st ->
                        ScrapeStrategy(
                            key = st.key,
                            searchPath = st.searchPath,
                            searchKeywordFormat = st.searchKeywordFormat,
                            maxPage = st.maxPage,
                            resultLinkSelector = st.resultLinkSelector,
                            requireQualityTag = st.requireQualityTag,
                            requireArtistInTitle = st.requireArtistInTitle,
                        )
                    },
                )
            },
        )
    }

    override suspend fun resolveAudio(title: String, artist: String): Result<ResolvedAudio> =
        suspendRunCatching {
            val response = try {
                importApiService.resolveAudio(
                    ResolveAudioRequest(title = title, artist = artist),
                )
            } catch (e: retrofit2.HttpException) {
                val msg = e.response()?.errorBody()?.string().orEmpty()
                throw IllegalStateException(
                    when {
                        e.code() == 404 -> "未解析到可用音频（或服务端未部署 resolve-audio）"
                        msg.isNotBlank() -> msg.take(200)
                        else -> "解析音频失败 HTTP ${e.code()}"
                    },
                )
            }
            val data = response.data
            if (!response.isSuccess || data == null || data.url.isBlank()) {
                throw IllegalStateException(response.message.ifBlank { "未解析到可用音频" })
            }
            ResolvedAudio(
                url = data.url,
                source = data.source,
                referer = data.referer,
                score = data.score,
                exact = data.exact,
            )
        }

    override suspend fun lookupSourceLink(
        title: String,
        artist: String,
        source: String,
    ): Result<SourceLink?> = suspendRunCatching {
        val response = sourceLinkApiService.lookup(title, artist, source)
        if (!response.isSuccess) throw IllegalStateException(response.message)
        response.data?.detailUrl?.takeIf { it.isNotBlank() }?.let {
            SourceLink(
                detailUrl = it,
                matchScore = response.data?.matchScore ?: 0,
                titleRaw = response.data?.titleRaw,
            )
        }
    }

    override suspend fun upsertSourceLink(
        title: String,
        artist: String,
        detailUrl: String,
        matchScore: Int,
        titleRaw: String?,
        source: String,
    ): Result<Unit> = suspendRunCatching {
        val response = sourceLinkApiService.upsert(
            UpsertSourceLinkRequest(
                title = title,
                artist = artist,
                source = source,
                detailUrl = detailUrl,
                matchScore = matchScore,
                titleRaw = titleRaw,
                verified = true,
            ),
        )
        if (!response.isSuccess) throw IllegalStateException(response.message)
    }

    override suspend fun reportUnreliableLink(
        title: String,
        artist: String,
        detailUrl: String,
        titleRaw: String?,
        source: String,
    ): Result<Unit> = suspendRunCatching {
        val response = sourceLinkApiService.reportUnreliable(
            UpsertSourceLinkRequest(
                title = title,
                artist = artist,
                source = source,
                detailUrl = detailUrl,
                matchScore = 0,
                titleRaw = titleRaw,
                verified = false,
            ),
        )
        if (!response.isSuccess) throw IllegalStateException(response.message)
    }

    override suspend fun fetchLyrics(platform: String, externalId: String): Result<ImportedLyrics> =
        suspendRunCatching {
            val response = importApiService.fetchLyrics(platform, externalId)
            if (!response.isSuccess) throw IllegalStateException(response.message)
            val data = response.data
            ImportedLyrics(
                lyric = data?.lyric.orEmpty(),
                lyricTrans = data?.lyricTrans.orEmpty(),
            )
        }

    override suspend fun findMusic(title: String, artist: String): Result<Long?> = suspendRunCatching {
        val response = musicApiService.findMusic(title, artist)
        if (!response.isSuccess) throw IllegalStateException(response.message)
        response.data?.musicId
    }

    override suspend fun findOrCreateMusic(
        title: String,
        url: String,
        coverImage: String?,
        durationMs: Long,
        artists: List<String>,
        lyrics: String,
        lyricsTrans: String,
        albumName: String?,
        albumCover: String?,
        trackNumber: Int,
        platform: String?,
        externalId: String?,
    ): Result<Long> = suspendRunCatching {
        val response = musicApiService.findOrCreateMusic(
            FindOrCreateMusicRequest(
                title = title,
                url = url,
                coverImage = coverImage,
                duration = durationMs.coerceAtLeast(0),
                lyrics = lyrics,
                lyricsTrans = lyricsTrans,
                artists = artists.map { FindOrCreateArtistRequest(it) },
                album = albumName?.takeIf { it.isNotBlank() }?.let {
                    FindOrCreateAlbumRequest(title = it, coverImage = albumCover)
                },
                trackNumber = trackNumber,
                platform = platform,
                externalId = externalId,
            ),
        )
        val data = response.data ?: throw IllegalStateException(response.message.ifBlank { "创建失败" })
        if (!response.isSuccess) throw IllegalStateException(response.message)
        data.musicId
    }

    override suspend fun updateImportSession(
        playlistId: Long,
        totalTracks: Int?,
        importedTracks: Int?,
        status: String?,
        error: String?,
    ): Result<Unit> = suspendRunCatching {
        val response = playlistApiService.updateImportSession(
            playlistId,
            ImportSessionRequest(
                totalTracks = totalTracks,
                importedTracks = importedTracks,
                status = status,
                error = error,
            ),
        )
        if (!response.isSuccess) throw IllegalStateException(response.message)
    }

    override suspend fun importMusic(url: String, platform: String?): Result<ImportMusicResponse> {
        return suspendRunCatching {
            val response = importApiService.importMusic(
                ImportMusicRequest(url = url, platform = platform),
            )
            val data = response.data
            if (response.isSuccess && data != null) {
                ImportMusicResponse(
                    albumId = data.albumId,
                    taskId = data.taskId,
                    isImporting = data.isImporting,
                )
            } else {
                throw IllegalStateException(response.message)
            }
        }
    }

    override suspend fun importNeteaseCharts(platform: String?): Result<ImportChartsResult> {
        return suspendRunCatching {
            val response = importApiService.importNeteaseCharts(
                ImportChartsRequest(platform = platform),
            )
            val data = response.data
            if (response.isSuccess && data != null) {
                ImportChartsResult(
                    total = data.total,
                    successCount = data.successCount,
                    failedCount = data.failedCount,
                )
            } else {
                throw IllegalStateException(response.message.ifBlank { "更新排行榜失败" })
            }
        }
    }

    override suspend fun importHotPlaylists(platform: String?): Result<String> {
        return suspendRunCatching {
            val response = importApiService.importHotPlaylists(
                ImportChartsRequest(platform = platform),
            )
            if (response.isSuccess) {
                response.message.ifBlank { "热门歌单同步任务已启动，正在后台处理" }
            } else {
                throw IllegalStateException(response.message.ifBlank { "更新热门歌单失败" })
            }
        }
    }
}
