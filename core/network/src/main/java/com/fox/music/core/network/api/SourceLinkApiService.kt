package com.fox.music.core.network.api

import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.SourceLinkDto
import com.fox.music.core.network.model.UpsertSourceLinkRequest
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SourceLinkApiService {

    @GET("/api/music/source-links/lookup")
    suspend fun lookup(
        @Query("title") title: String,
        @Query("artist") artist: String = "",
        @Query("source") source: String = "hifinicn",
    ): ApiResponse<SourceLinkDto?>

    @POST("/api/music/source-links")
    suspend fun upsert(
        @Body body: UpsertSourceLinkRequest,
    ): ApiResponse<JsonElement?>

    @POST("/api/music/source-links/unreliable")
    suspend fun reportUnreliable(
        @Body body: UpsertSourceLinkRequest,
    ): ApiResponse<JsonElement?>
}
