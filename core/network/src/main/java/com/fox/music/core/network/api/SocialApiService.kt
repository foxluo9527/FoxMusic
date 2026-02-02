package com.fox.music.core.network.api

import com.fox.music.core.network.model.*
import retrofit2.http.*

interface SocialApiService {

    // Friends
    @GET("api/friends")
    suspend fun getFriends(): ApiResponse<List<FriendDto>>

    @GET("api/friends/requests")
    suspend fun getFriendRequests(): ApiResponse<List<FriendRequestDto>>

    @GET("api/friends/search")
    suspend fun searchUsers(
        @Query("keyword") keyword: String
    ): ApiResponse<List<SearchedUserDto>>

    @POST("api/friends/request")
    suspend fun sendFriendRequest(@Body request: FriendRequestBody): ApiResponse<Unit>

    @POST("api/friends/accept")
    suspend fun acceptFriendRequest(@Body request: AcceptFriendRequest): ApiResponse<Unit>

    @DELETE("api/friends/{friendId}")
    suspend fun deleteFriend(@Path("friendId") friendId: Long): ApiResponse<Unit>

    @POST("api/friends/remark")
    suspend fun setFriendRemark(@Body request: SetRemarkRequest): ApiResponse<Unit>

    // Posts
    @GET("api/posts")
    suspend fun getPosts(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("keyword") keyword: String? = null
    ): ApiResponse<PagedResponse<PostDto>>

    @POST("api/posts")
    suspend fun createPost(@Body request: CreatePostRequest): ApiResponse<PostDto>

    @GET("api/posts/{id}")
    suspend fun getPostDetail(@Path("id") id: Long): ApiResponse<PostDto>

    @DELETE("api/posts/{id}")
    suspend fun deletePost(@Path("id") id: Long): ApiResponse<Unit>

    @POST("api/posts/{id}/like")
    suspend fun togglePostLike(@Path("id") id: Long): ApiResponse<Unit>

    @GET("api/posts/{id}/comments")
    suspend fun getPostComments(
        @Path("id") id: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<PagedResponse<CommentDto>>

    @POST("api/posts/{id}/comments")
    suspend fun postComment(
        @Path("id") id: Long,
        @Body request: PostCommentRequest
    ): ApiResponse<CommentDto>

    // Favorites
    @GET("api/favorites")
    suspend fun getFavorites(
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<PagedResponse<FavoriteDto>>

    // Notifications
    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<PagedResponse<NotificationDto>>

    @GET("api/notifications/unread")
    suspend fun getUnreadCount(): ApiResponse<UnreadCountDto>

    @POST("api/notifications/read")
    suspend fun markAsRead(@Body request: MarkReadRequest): ApiResponse<Unit>

    @POST("api/notifications/read-all")
    suspend fun markAllAsRead(): ApiResponse<Unit>

    @HTTP(method = "DELETE", path = "api/notifications", hasBody = true)
    suspend fun deleteNotifications(@Body request: DeleteNotificationsRequest): ApiResponse<Unit>
}
