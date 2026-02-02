package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.Album
import com.fox.music.core.model.Artist
import com.fox.music.core.model.Music
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.Playlist
import com.fox.music.core.model.SearchHistory
import kotlinx.coroutines.flow.Flow

interface SearchRepository {

    suspend fun searchMusic(keyword: String, page: Int = 1, limit: Int = 20): Result<PagedData<Music>>

    suspend fun getHotKeywords(type: String? = null, limit: Int = 10): Result<List<String>>

    fun getSearchHistory(limit: Int = 20): Flow<List<SearchHistory>>

    suspend fun saveSearchHistory(keyword: String): Result<Unit>

    suspend fun clearSearchHistory(): Result<Unit>

    suspend fun removeSearchHistoryItem(keyword: String): Result<Unit>
}
