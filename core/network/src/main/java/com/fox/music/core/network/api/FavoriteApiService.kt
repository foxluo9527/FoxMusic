package com.fox.music.core.network.api

import com.fox.music.core.network.model.AlbumDto
import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.ArtistDto
import com.fox.music.core.network.model.MusicDto
import com.fox.music.core.network.model.PagedResponse
import com.fox.music.core.network.model.PlaylistDto
import retrofit2.http.GET
import retrofit2.http.Query

interface FavoriteApiService {

    @GET("api/favorites/music")
    suspend fun getFavoriteMusics(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): ApiResponse<PagedResponse<MusicDto>>

    @GET("api/favorites/artists")
    suspend fun getFavoriteArtists(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): ApiResponse<PagedResponse<ArtistDto>>

    @GET("api/favorites/albums")
    suspend fun getFavoriteAlbums(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): ApiResponse<PagedResponse<AlbumDto>>

    @GET("api/favorites/playlists")
    suspend fun getFavoritePlaylists(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): ApiResponse<PagedResponse<PlaylistDto>>
}
