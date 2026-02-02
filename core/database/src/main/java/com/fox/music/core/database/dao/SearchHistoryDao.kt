package com.fox.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fox.music.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC")
    fun getAllSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun getRecentSearchHistory(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE query LIKE '%' || :query || '%' ORDER BY searchedAt DESC")
    fun searchHistory(query: String): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchQueryById(id: Long)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchQueryByText(query: String)

    @Query("DELETE FROM search_history")
    suspend fun deleteAllSearchHistory()
}
