package com.fox.music.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.fox.music.core.database.entity.MusicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    @Query("SELECT * FROM musics ORDER BY cachedAt DESC")
    fun getAllMusics(): Flow<List<MusicEntity>>

    @Query("SELECT * FROM musics ORDER BY cachedAt DESC")
    fun getMuscisPaging(): PagingSource<Int, MusicEntity>

    @Query("SELECT * FROM musics WHERE id = :id")
    suspend fun getMusicById(id: Long): MusicEntity?

    @Query("SELECT * FROM musics WHERE id = :id")
    fun observeMusicById(id: Long): Flow<MusicEntity?>

    @Query("SELECT * FROM musics WHERE title LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%' OR artistsJson LIKE '%' || :query || '%'")
    fun searchMusics(query: String): Flow<List<MusicEntity>>

    @Query("SELECT * FROM musics WHERE isFavorite = 1 ORDER BY cachedAt DESC")
    fun getFavoriteMusics(): Flow<List<MusicEntity>>

    @Query("SELECT * FROM musics WHERE genre = :genre ORDER BY cachedAt DESC")
    fun getMusicsByGenre(genre: String): Flow<List<MusicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusic(music: MusicEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusics(musics: List<MusicEntity>)

    @Upsert
    suspend fun upsertMusic(music: MusicEntity)

    @Upsert
    suspend fun upsertMusics(musics: List<MusicEntity>)

    @Update
    suspend fun updateMusic(music: MusicEntity)

    @Delete
    suspend fun deleteMusic(music: MusicEntity)

    @Query("DELETE FROM musics WHERE id = :id")
    suspend fun deleteMusicById(id: Long)

    @Query("DELETE FROM musics")
    suspend fun deleteAllMusics()

    @Query("UPDATE musics SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)

    @Query("UPDATE musics SET playCount = playCount + 1 WHERE id = :id")
    suspend fun incrementPlayCount(id: Long)
}
