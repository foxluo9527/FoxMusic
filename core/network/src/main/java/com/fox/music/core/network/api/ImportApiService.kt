package com.fox.music.core.network.api

import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.ImportChartsBatchDto
import com.fox.music.core.network.model.ImportChartsRequest
import com.fox.music.core.network.model.ImportLyricsDto
import com.fox.music.core.network.model.ImportMusicRequest
import com.fox.music.core.network.model.ImportMusicResponse
import com.fox.music.core.network.model.ParsePlaylistRequest
import com.fox.music.core.network.model.ParsedPlaylistDto
import com.fox.music.core.network.model.ResolveAudioDto
import com.fox.music.core.network.model.ResolveAudioRequest
import com.fox.music.core.network.model.ScrapeConfigDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ImportApiService {

    @POST("/api/import/parse")
    suspend fun parsePlaylist(
        @Body body: ParsePlaylistRequest,
    ): ApiResponse<ParsedPlaylistDto>

    @GET("/api/import/scrape-config")
    suspend fun getScrapeConfig(): ApiResponse<ScrapeConfigDto>

    @POST("/api/import/resolve-audio")
    suspend fun resolveAudio(
        @Body body: ResolveAudioRequest,
    ): ApiResponse<ResolveAudioDto>

    @GET("/api/import/lyrics")
    suspend fun fetchLyrics(
        @Query("platform") platform: String,
        @Query("id") id: String,
    ): ApiResponse<ImportLyricsDto>

    @POST("/api/import/music")
    suspend fun importMusic(
        @Body body: ImportMusicRequest,
    ): ApiResponse<ImportMusicResponse>

    @POST("/api/import/netease/charts")
    suspend fun importNeteaseCharts(
        @Body body: ImportChartsRequest,
    ): ApiResponse<ImportChartsBatchDto>

    @POST("/api/import/hot-playlists")
    suspend fun importHotPlaylists(
        @Body body: ImportChartsRequest,
    ): ApiResponse<JsonElement?>
}
