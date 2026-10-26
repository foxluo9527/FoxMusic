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
import com.fox.music.core.network.model.SearchImportSourcesDto
import com.fox.music.core.network.model.SearchImportSourcesRequest
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

    /**
     * 服务端 API 多源音源候选（音源纠错同款链路：网易云/酷我/Hifini解析/插件源）。
     * 导入场景逐条试下载用，比单结果兜底更抗单源失效。
     */
    @POST("/api/import/search-sources")
    suspend fun searchImportSources(
        @Body body: SearchImportSourcesRequest,
    ): ApiResponse<SearchImportSourcesDto>

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
