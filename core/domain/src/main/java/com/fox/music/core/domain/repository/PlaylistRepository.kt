package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.Playlist
import com.fox.music.core.model.PlaylistCategory
import com.fox.music.core.model.PlaylistDetail

interface PlaylistRepository {

    suspend fun getPlaylists(userId: Long? = null): Result<List<Playlist>>

    suspend fun getPlaylistDetail(id: Long, page: Int = 1, limit: Int = 20): Result<PlaylistDetail>

    suspend fun createPlaylist(
        title: String,
        description: String? = null,
        coverImage: String? = null,
        isPublic: Boolean = true,
        tagIds: List<Long>? = null
    ): Result<Playlist>

    suspend fun updatePlaylist(
        id: Long,
        title: String? = null,
        description: String? = null,
        coverImage: String? = null,
        isPublic: Boolean? = null,
        tagIds: List<Long>? = null
    ): Result<Playlist>

    suspend fun deletePlaylist(id: Long): Result<Unit>

    suspend fun getRecommendedPlaylists(page: Int = 1, limit: Int = 20): Result<PagedData<Playlist>>

    suspend fun addTracks(playlistId: Long, musicIds: List<Long>): Result<Unit>

    suspend fun removeTrack(playlistId: Long, musicId: Long): Result<Unit>

    suspend fun removeTracks(playlistId: Long, musicIds: List<Long>): Result<Unit>

    suspend fun getCategories(categoryType: String? = null): Result<List<PlaylistCategory>>

    suspend fun getRecommendedCategories(): Result<List<PlaylistCategory>>

    suspend fun getFixedCategories(): Result<List<PlaylistCategory>>

    suspend fun getCategoryDetail(id: Long): Result<PlaylistCategory>

    suspend fun getCategoryPlaylists(
        categoryId: Long,
        page: Int = 1,
        limit: Int = 20
    ): Result<PagedData<Playlist>>
}
