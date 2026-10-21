package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.cache.SearchMusicResultCache
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.database.dao.SearchHistoryDao
import com.fox.music.core.database.entity.SearchHistoryEntity
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.model.music.ProviderCandidateGroupDto
import com.fox.music.core.model.music.ProviderCandidateGroup
import com.fox.music.core.model.music.ProviderCandidateDto
import com.fox.music.core.model.music.ProviderCandidate
import com.fox.music.core.model.music.CandidatePlayType
import com.fox.music.core.model.music.CandidateResolveInfo
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.ImportSingleRequest
import com.fox.music.core.model.music.ImportSingleResponseDto
import com.fox.music.core.model.music.ImportSingleResult
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.MusicExists
import com.fox.music.core.model.music.MusicExistsDto
import com.fox.music.core.model.music.ProviderSearchResults
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.ResolveThirdPartyDto
import com.fox.music.core.model.music.ResolveThirdPartyRequest
import com.fox.music.core.model.music.ScrapedLyricsDto
import com.fox.music.core.model.music.SearchHistory
import com.fox.music.core.model.music.SearchSuggest
import com.fox.music.core.network.api.MusicApiService
import com.fox.music.core.network.api.SearchApiService
import com.fox.music.core.network.model.ProviderSearchResultsDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val musicApi: MusicApiService,
    private val searchApi: SearchApiService,
    private val searchHistoryDao: SearchHistoryDao,
    private val searchMusicResultCache: SearchMusicResultCache,
) : SearchRepository {

    override suspend fun searchMusic(
        keyword: String,
        page: Int,
        limit: Int,
        platform: String?
    ): Result<PagedData<Music>> = suspendRunCatching {
        val trimmed = keyword.trim()
        searchMusicResultCache.get(trimmed, platform, page, limit)?.let { cached ->
            return@suspendRunCatching cached
        }

        val response = if (platform.isNullOrBlank()) {
            musicApi.getMusicList(page, limit, trimmed, null, null)
        } else {
            musicApi.searchThirdPartyMusic(
                platform = platform,
                keyword = trimmed,
                page = page,
                limit = limit
            )
        }
        val data = response.data
        if (response.isSuccess && data != null) {
            val paged = data.toPagedData { it.toMusic() }
            searchMusicResultCache.put(trimmed, platform, page, limit, paged)
            paged
        } else throw Exception(response.message)
    }

    override suspend fun getHotKeywords(type: String?, limit: Int): Result<List<HotKeyword>> =
        suspendRunCatching {
            val response = searchApi.getHotKeywords(type, limit)
            if (response.isSuccess) {
                @Suppress("UNCHECKED_CAST")
                (response.data?.map {HotKeyword(it.keyword, it.type, it.searchCount)})
                    ?: emptyList()
            } else {
                throw IllegalStateException(response.message)
            }
        }

    override suspend fun getSuggest(keyword: String, limit: Int): Result<List<SearchSuggest>> =
        suspendRunCatching {
            val trimmed = keyword.trim()
            if (trimmed.isEmpty()) return@suspendRunCatching emptyList()
            val response = searchApi.getSuggest(trimmed, limit)
            val data = response.data
            if (response.isSuccess && data != null) {
                data.suggestions.map {
                    SearchSuggest(
                        id = it.id,
                        text = it.text,
                        type = it.type,
                        cover = it.cover,
                    )
                }
            } else {
                throw IllegalStateException(response.message)
            }
        }

    override fun getSearchHistory(limit: Int): Flow<List<SearchHistory>> =
        searchHistoryDao.getRecentSearchHistory(limit).map { list ->
            list.map { entity ->
                SearchHistory(
                    id = entity.id,
                    keyword = entity.query,
                    searchTime = SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss",
                        Locale.getDefault()
                    ).format(Date(entity.searchedAt))
                )
            }.distinctBy { it.keyword }
        }

    override suspend fun saveSearchHistory(keyword: String): Result<Unit> = suspendRunCatching {
        val trimmed = keyword.trim()
        if (trimmed.isNotEmpty()) {
            searchHistoryDao.deleteSearchQueryByText(trimmed)
            searchHistoryDao.insertSearchQuery(SearchHistoryEntity(query = trimmed))
        }
        Unit
    }

    override suspend fun clearSearchHistory(): Result<Unit> = suspendRunCatching {
        val response = searchApi.clearSearchHistory(null)
        if (!response.isSuccess) {
            throw IllegalStateException(response.message)
        }
        searchHistoryDao.deleteAllSearchHistory()
        Unit
    }

    override suspend fun removeSearchHistoryItem(keyword: String): Result<Unit> =
        suspendRunCatching {
            searchHistoryDao.deleteSearchQueryByText(keyword)
            Unit
        }

    override suspend fun searchProvidersByTrack(
        title: String,
        artist: String?,
        platforms: List<String>?,
        limit: Int,
    ): Result<ProviderSearchResults> = suspendRunCatching {
        val platformsParam = platforms?.joinToString(",")?.takeIf { it.isNotBlank() }
        val response = musicApi.searchProvidersByTrack(
            title = title,
            artist = artist,
            platforms = platformsParam,
            limit = limit,
        )
        if (!response.isSuccess || response.data == null) {
            throw IllegalStateException(response.message.takeIf { it.isNotBlank() } ?: "多源搜索失败")
        }
        val dto = response.data as ProviderSearchResultsDto
        val groups = dto.nonEmptyGroups().associate { (provider, list) ->
            provider to list.mapNotNull { it.toMusic() }
        }
        ProviderSearchResults(groups = groups)
    }

    override suspend fun searchProviderCandidates(
        title: String,
        artist: String?,
        durationMs: Long?,
        groups: List<String>?,
    ): Result<List<ProviderCandidateGroup>> = suspendRunCatching {
        val groupsParam = groups?.joinToString(",")?.takeIf { it.isNotBlank() }
        val response = musicApi.getProviderCandidates(
            title = title,
            artist = artist,
            durationMs = durationMs,
            groups = groupsParam,
        )
        if (!response.isSuccess || response.data == null) {
            throw IllegalStateException(response.message.takeIf { it.isNotBlank() } ?: "多源候选搜索失败")
        }
        (response.data as List<ProviderCandidateGroupDto>).map { g ->
            ProviderCandidateGroup(
                key = g.key,
                label = g.label,
                playType = CandidatePlayType.from(g.playType),
                items = g.items.map { it.toCandidate() },
            )
        }
    }

    override suspend fun resolveThirdPartyAudio(req: ResolveThirdPartyRequest): Result<ResolveThirdPartyDto> =
        suspendRunCatching {
            val response = musicApi.resolveThirdPartyAudio(req)
            if (!response.isSuccess || response.data == null) {
                throw IllegalStateException(response.message.takeIf { it.isNotBlank() } ?: "音源解析失败")
            }
            response.data as ResolveThirdPartyDto
        }

    override suspend fun checkMusicExists(title: String, artist: String?): Result<MusicExists> =
        suspendRunCatching {
            val response = musicApi.checkMusicExists(title = title, artist = artist)
            if (!response.isSuccess || response.data == null) {
                throw IllegalStateException(response.message.takeIf { it.isNotBlank() } ?: "收录状态查询失败")
            }
            val dto = response.data as MusicExistsDto
            MusicExists(exists = dto.exists, musicId = dto.musicId)
        }

    override suspend fun importSingleTrack(req: ImportSingleRequest): Result<ImportSingleResult> =
        suspendRunCatching {
            val response = musicApi.importSingleTrack(req)
            if (!response.isSuccess || response.data == null) {
                throw IllegalStateException(response.message.takeIf { it.isNotBlank() } ?: "收录失败")
            }
            val dto = response.data as ImportSingleResponseDto
            ImportSingleResult(
                musicId = dto.musicId,
                isNew = dto.isNew,
                playlistId = dto.playlistId,
                alreadyInPlaylist = dto.alreadyInPlaylist,
            )
        }

    override suspend fun fetchScrapedLyrics(
        title: String,
        artist: String,
        durationMs: Long?,
    ): Result<String?> = suspendRunCatching {
        val response = musicApi.fetchScrapedLyrics(
            title = title,
            artist = artist,
            durationMs = durationMs?.takeIf { it > 0 },
        )
        // 歌词刮削是尽力而为：HTTP 层失败（超时/限流）静默返回 null，不阻塞收录
        val dto = response.data as? ScrapedLyricsDto ?: return@suspendRunCatching null
        (dto.synced ?: dto.lyric ?: dto.plain)?.takeIf { it.isNotBlank() }
    }
}

/** 渠道候选 DTO → 领域模型 */
private fun ProviderCandidateDto.toCandidate(): ProviderCandidate = ProviderCandidate(
    id = id,
    title = title,
    artist = artist,
    durationMs = durationMs,
    playType = CandidatePlayType.from(playType),
    directUrl = url,
    referer = referer,
    score = score,
    exact = exact,
    resolveProvider = resolveProvider,
    resolveParams = resolveParams?.let {
        CandidateResolveInfo(
            site = it.site,
            detailUrl = it.detailUrl,
            title = it.title,
            artist = it.artist,
            durationMs = it.durationMs,
        )
    },
)
