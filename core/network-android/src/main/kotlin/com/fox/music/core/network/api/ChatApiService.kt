package com.fox.music.core.network.api

import com.fox.music.core.network.model.*
import retrofit2.http.*

interface ChatApiService {

    @POST("api/messages")
    suspend fun sendMessage(@Body request: SendMessageRequest): ApiResponse<MessageDto>

    @POST("api/messages/share")
    suspend fun sendShareMessage(@Body request: ShareMessageRequest): ApiResponse<MessageDto>

    @POST("api/messages/")
    suspend fun sendMessage(@Body body: Map<String, String>): ApiResponse<MessageDto>

    @POST("api/messages/{id}/recall")
    suspend fun recallMessage(@Path("id") id: Long): ApiResponse<Unit>

    @POST("api/messages/read")
    suspend fun markAsRead(@Body request: MarkChatReadRequest): ApiResponse<Unit>

    /** @param peerUserId 对方用户 ID；传 0 获取全部未读 */
    @GET("api/messages/unread/{peerUserId}")
    suspend fun getUnreadMessages(
        @Path("peerUserId") peerUserId: Long,
    ): ApiResponse<List<MessageDto>>

    @GET("api/messages/conversations")
    suspend fun getConversations(): ApiResponse<List<ConversationDto>>

    @GET("api/messages/history/{userId}")
    suspend fun getChatHistory(
        @Path("userId") userId: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<PagedResponse<MessageDto>>
}
