package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.Comment
import com.fox.music.core.model.Favorite
import com.fox.music.core.model.Friend
import com.fox.music.core.model.FriendRequest
import com.fox.music.core.model.Notification
import com.fox.music.core.model.Post
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.SearchedUser

interface SocialRepository {

    suspend fun getFriends(): Result<List<Friend>>

    suspend fun getFriendRequests(): Result<List<FriendRequest>>

    suspend fun searchUsers(keyword: String): Result<List<SearchedUser>>

    suspend fun sendFriendRequest(userId: Long, message: String? = null): Result<Unit>

    suspend fun acceptFriendRequest(requestId: Long): Result<Unit>

    suspend fun deleteFriend(friendId: Long): Result<Unit>

    suspend fun setFriendRemark(friendId: Long, remark: String): Result<Unit>

    suspend fun getPosts(
        page: Int = 1,
        limit: Int = 20,
        keyword: String? = null
    ): Result<PagedData<Post>>

    suspend fun createPost(content: String, images: List<String> = emptyList(), musicId: Long? = null): Result<Post>

    suspend fun getPostDetail(id: Long): Result<Post>

    suspend fun deletePost(id: Long): Result<Unit>

    suspend fun togglePostLike(id: Long): Result<Unit>

    suspend fun getPostComments(id: Long, page: Int = 1, limit: Int = 20): Result<PagedData<Comment>>

    suspend fun postComment(postId: Long, content: String, parentId: Long? = null): Result<Comment>

    suspend fun getFavorites(
        type: String? = null,
        page: Int = 1,
        limit: Int = 20
    ): Result<PagedData<Favorite>>

    suspend fun getNotifications(page: Int = 1, limit: Int = 20): Result<PagedData<Notification>>

    suspend fun getUnreadNotificationCount(): Result<Int>

    suspend fun markNotificationRead(notificationIds: List<Long>): Result<Unit>

    suspend fun markAllNotificationsRead(): Result<Unit>

    suspend fun deleteNotifications(notificationIds: List<Long>): Result<Unit>
}
