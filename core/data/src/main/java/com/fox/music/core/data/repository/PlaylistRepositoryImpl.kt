package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.data.mapper.toPlaylist
import com.fox.music.core.data.mapper.toPlaylistCategory
import com.fox.music.core.data.mapper.toPlaylistDetail
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.Playlist
import com.fox.music.core.model.PlaylistCategory
import com.fox.music.core.model.PlaylistDetail
import com.fox.music.core.network.api.PlaylistApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistApi: PlaylistApiService
) : PlaylistRepository {

    override suspend fun getPlaylists(userId: Long?): Result<List<Playlist>> = suspendRunCatching {
        val response = playlistApi.getPlaylists(userId)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.map { it.toPlaylist() }
        } else throw Exception(response.message)
    }

    override suspend fun getPlaylistDetail(
        id: Long,
        page: Int,
        limit: Int
    ): Result<PlaylistDetail> = suspendRunCatching {
        val response = playlistApi.getPlaylistDetail(id, page, limit)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPlaylistDetail()
        } else throw Exception(response.message)
    }

    override suspend fun createPlaylist(
        title: String,
        description: String?,
        coverImage: String?,
        isPublic: Boolean,
        tagIds: List<Long>?
    ): Result<Playlist> = suspendRunCatching {
        val response = playlistApi.createPlaylist(
            com.fox.music.core.network.model.CreatePlaylistRequest(
                title = title,
                description = description,
                coverImage = coverImage,
                isPublic = isPublic,
                tags = tagIds
            )
        )
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPlaylist()
        } else throw Exception(response.message)
    }

    override suspend fun updatePlaylist(
        id: Long,
        title: String?,
        description: String?,
        coverImage: String?,
        isPublic: Boolean?,
        tagIds: List<Long>?
    ): Result<Playlist> = suspendRunCatching {
        val response = playlistApi.updatePlaylist(
            id,
            com.fox.music.core.network.model.UpdatePlaylistRequest(
                title = title,
                description = description,
                coverImage = coverImage,
                isPublic = isPublic,
                tags = tagIds
            )
        )
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPlaylist()
        } else throw Exception(response.message)
    }

    override suspend fun deletePlaylist(id: Long): Result<Unit> = suspendRunCatching {
        val response = playlistApi.deletePlaylist(id)
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun getRecommendedPlaylists(
        page: Int,
        limit: Int
    ): Result<PagedData<Playlist>> = suspendRunCatching {
        val response = playlistApi.getRecommendedPlaylists(page, limit)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toPlaylist() }
        } else throw Exception(response.message)
    }

    override suspend fun addTracks(playlistId: Long, musicIds: List<Long>): Result<Unit> =
        suspendRunCatching {
            val response = playlistApi.addTracks(
                playlistId,
                com.fox.music.core.network.model.AddTracksRequest(musicIds = musicIds)
            )
            if (response.isSuccess) Unit else throw Exception(response.message)
        }

    override suspend fun removeTrack(playlistId: Long, musicId: Long): Result<Unit> =
        suspendRunCatching {
            val response = playlistApi.removeTrack(playlistId, musicId)
            if (response.isSuccess) Unit else throw Exception(response.message)
        }

    override suspend fun removeTracks(playlistId: Long, musicIds: List<Long>): Result<Unit> =
        suspendRunCatching {
            val response = playlistApi.removeTracks(
                playlistId,
                com.fox.music.core.network.model.RemoveTracksRequest(musicIds = musicIds)
            )
            if (response.isSuccess) Unit else throw Exception(response.message)
        }

    override suspend fun getCategories(categoryType: String?): Result<List<PlaylistCategory>> =
        suspendRunCatching {
            val response = playlistApi.getCategories(categoryType)
            val data = response.data
            if (response.isSuccess && data != null) {
                data.map { it.toPlaylistCategory() }
            } else throw Exception(response.message)
        }

    override suspend fun getRecommendedCategories(): Result<List<PlaylistCategory>> =
        suspendRunCatching {
            val response = playlistApi.getRecommendedCategories()
            val data = response.data
            if (response.isSuccess && data != null) {
                data.map { it.toPlaylistCategory() }
            } else throw Exception(response.message)
        }

    override suspend fun getFixedCategories(): Result<List<PlaylistCategory>> = suspendRunCatching {
        val response = playlistApi.getFixedCategories()
        val data = response.data
        if (response.isSuccess && data != null) {
            data.map { it.toPlaylistCategory() }
        } else throw Exception(response.message)
    }

    override suspend fun getCategoryDetail(id: Long): Result<PlaylistCategory> = suspendRunCatching {
        val response = playlistApi.getCategoryDetail(id)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPlaylistCategory()
        } else throw Exception(response.message)
    }

    override suspend fun getCategoryPlaylists(
        categoryId: Long,
        page: Int,
        limit: Int
    ): Result<PagedData<Playlist>> = suspendRunCatching {
        val response = playlistApi.getCategoryPlaylists(categoryId, page, limit)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toPlaylist() }
        } else throw Exception(response.message)
    }
}
