package com.fox.music.core.network.api

import com.fox.music.core.assistant.AiChatRequest
import com.fox.music.core.assistant.AiChatResult
import com.fox.music.core.assistant.AiConversationDetail
import com.fox.music.core.assistant.AiConversationPage
import com.fox.music.core.network.model.ApiResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 客户端 AI 音乐助手（/api/ai）。
 * 契约见后端 routes/client/ai.js + services/aiAssistant.js。
 */
interface AiAssistantApiService {

    @POST("api/ai/chat")
    suspend fun chat(@Body request: AiChatRequest): ApiResponse<AiChatResult>

    @GET("api/ai/conversations")
    suspend fun listConversations(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): ApiResponse<AiConversationPage>

    @GET("api/ai/conversations/{id}")
    suspend fun getConversation(@Path("id") id: Long): ApiResponse<AiConversationDetail>

    @DELETE("api/ai/conversations/{id}")
    suspend fun deleteConversation(@Path("id") id: Long): ApiResponse<Unit>
}
