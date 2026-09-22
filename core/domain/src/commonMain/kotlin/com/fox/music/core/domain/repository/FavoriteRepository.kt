package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist

interface FavoriteRepository {

    suspend fun getFavoriteMusics(
        page: Int = 1,
        limit: Int = 20,
    ): Result<PagedData<Music>>

    suspend fun getFavoriteArtists(
        page: Int = 1,
        limit: Int = 20,
    ): Result<PagedData<Artist>>

    suspend fun getFavoriteAlbums(
        page: Int = 1,
        limit: Int = 20,
    ): Result<PagedData<Album>>

    suspend fun getFavoritePlaylists(
        page: Int = 1,
        limit: Int = 20,
    ): Result<PagedData<Playlist>>
}
