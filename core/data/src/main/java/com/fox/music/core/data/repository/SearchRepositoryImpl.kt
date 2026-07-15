package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.cache.SearchMusicResultCache
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.database.dao.SearchHistoryDao
import com.fox.music.core.database.entity.SearchHistoryEntity
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.SearchHistory
import com.fox.music.core.model.music.SearchSuggest
import com.fox.music.core.network.api.MusicApiService
import com.fox.music.core.network.api.SearchApiService
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
}
