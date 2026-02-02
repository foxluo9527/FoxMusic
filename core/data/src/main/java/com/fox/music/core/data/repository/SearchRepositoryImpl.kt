package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.database.dao.SearchHistoryDao
import com.fox.music.core.database.entity.SearchHistoryEntity
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.model.Music
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.SearchHistory
import com.fox.music.core.network.api.MusicApiService
import com.fox.music.core.network.api.SearchApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val musicApi: MusicApiService,
    private val searchApi: SearchApiService,
    private val searchHistoryDao: SearchHistoryDao
) : SearchRepository {

    override suspend fun searchMusic(
        keyword: String,
        page: Int,
        limit: Int
    ): Result<PagedData<Music>> = suspendRunCatching {
        val response = musicApi.getMusicList(page, limit, keyword, null, null)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toMusic() }
        } else throw Exception(response.message)
    }

    override suspend fun getHotKeywords(type: String?, limit: Int): Result<List<String>> =
        suspendRunCatching {
            val response = searchApi.getHotKeywords(type, limit)
            if (response.isSuccess) {
                @Suppress("UNCHECKED_CAST")
                (response.data as? List<String>) ?: emptyList()
            } else emptyList()
        }

    override fun getSearchHistory(limit: Int): Flow<List<SearchHistory>> =
        searchHistoryDao.getRecentSearchHistory(limit).map { list ->
            list.map { entity ->
                SearchHistory(
                    id = entity.id,
                    keyword = entity.query,
                    searchTime = java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(entity.searchedAt))
                )
            }
        }

    override suspend fun saveSearchHistory(keyword: String): Result<Unit> = suspendRunCatching {
        val trimmed = keyword.trim()
        if (trimmed.isNotEmpty()) {
            searchHistoryDao.insertSearchQuery(SearchHistoryEntity(query = trimmed))
        }
        Unit
    }

    override suspend fun clearSearchHistory(): Result<Unit> = suspendRunCatching {
        searchHistoryDao.deleteAllSearchHistory()
        searchApi.clearSearchHistory(null)
        Unit
    }

    override suspend fun removeSearchHistoryItem(keyword: String): Result<Unit> =
        suspendRunCatching {
            searchHistoryDao.deleteSearchQueryByText(keyword)
            Unit
        }
}
