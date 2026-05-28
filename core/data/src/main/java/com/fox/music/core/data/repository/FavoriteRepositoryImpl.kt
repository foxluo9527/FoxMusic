package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toAlbum
import com.fox.music.core.data.mapper.toArtist
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.data.mapper.toPlaylist
import com.fox.music.core.domain.repository.FavoriteRepository
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.network.api.FavoriteApiService
import javax.inject.Inject

class FavoriteRepositoryImpl @Inject constructor(
    private val favoriteApiService: FavoriteApiService,
) : FavoriteRepository {

    override suspend fun getFavoriteMusics(
        page: Int,
        limit: Int,
    ): Result<PagedData<Music>> = suspendRunCatching {
        val response = favoriteApiService.getFavoriteMusics(page, limit)
        response.data?.toPagedData { it.toMusic().copy(isFavorite = true) }
            ?: PagedData(emptyList(), 0, 0)
    }

    override suspend fun getFavoriteArtists(
        page: Int,
        limit: Int,
    ): Result<PagedData<Artist>> = suspendRunCatching {
        val response = favoriteApiService.getFavoriteArtists(page, limit)
        response.data?.toPagedData { it.toArtist().copy(isFavorite = true) }
            ?: PagedData(emptyList(), 0, 0)
    }

    override suspend fun getFavoriteAlbums(
        page: Int,
        limit: Int,
    ): Result<PagedData<Album>> = suspendRunCatching {
        val response = favoriteApiService.getFavoriteAlbums(page, limit)
        response.data?.toPagedData { it.toAlbum().copy(isFavorite = true) }
            ?: PagedData(emptyList(), 0, 0)
    }

    override suspend fun getFavoritePlaylists(
        page: Int,
        limit: Int,
    ): Result<PagedData<Playlist>> = suspendRunCatching {
        val response = favoriteApiService.getFavoritePlaylists(page, limit)
        response.data?.toPagedData { it.toPlaylist().copy(isFavorite = true) }
            ?: PagedData(emptyList(), 0, 0)
    }
}
