package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.FavoriteRepository
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import javax.inject.Inject

class GetFavoritesUseCase @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
) {
    suspend fun getMusics(
        page: Int = 1,
        limit: Int = 20,
    ): Result<PagedData<Music>> = favoriteRepository.getFavoriteMusics(page, limit)

    suspend fun getArtists(
        page: Int = 1,
        limit: Int = 20,
    ): Result<PagedData<Artist>> = favoriteRepository.getFavoriteArtists(page, limit)

    suspend fun getAlbums(
        page: Int = 1,
        limit: Int = 20,
    ): Result<PagedData<Album>> = favoriteRepository.getFavoriteAlbums(page, limit)

    suspend fun getPlaylists(
        page: Int = 1,
        limit: Int = 20,
    ): Result<PagedData<Playlist>> = favoriteRepository.getFavoritePlaylists(page, limit)
}
