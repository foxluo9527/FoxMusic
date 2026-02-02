package com.fox.music.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.fox.music.core.database.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylistById(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE ownerId = :ownerId ORDER BY updatedAt DESC")
    fun getPlaylistsByOwner(ownerId: Long): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE name LIKE '%' || :query || '%'")
    fun searchPlaylists(query: String): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()
}
