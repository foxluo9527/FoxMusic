package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.music.Favorite
import com.fox.music.core.model.chat.Friend
import com.fox.music.core.model.chat.FriendRequest
import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.chat.SearchedUser

interface SocialRepository {

    suspend fun getFriends(): Result<List<Friend>>

    suspend fun getFriendRequests(): Result<List<FriendRequest>>

    suspend fun searchUsers(keyword: String): Result<List<SearchedUser>>

    suspend fun sendFriendRequest(userId: Long, message: String, mark: String? = null): Result<Unit>

    suspend fun acceptFriendRequest(requestId: Long): Result<Unit>

    suspend fun deleteFriend(friendId: Long): Result<Unit>

    suspend fun setFriendRemark(friendId: Long, remark: String): Result<Unit>

    suspend fun getPosts(
        page: Int = 1,
        limit: Int = 20,
        keyword: String? = null,
        sort: String = "latest",
        tag: String? = null,
        authorId: Long? = null,
    ): Result<PagedData<Post>>

    suspend fun getPostTags(limit: Int = 30, keyword: String? = null): Result<List<com.fox.music.core.model.music.Tag>>

    suspend fun getRecommendedPostTags(
        content: String? = null,
        musicId: Long? = null,
        limit: Int = 20,
    ): Result<List<com.fox.music.core.model.music.Tag>>

    suspend fun createPost(
        content: String,
        images: List<String> = emptyList(),
        media: List<com.fox.music.core.model.social.PostMedia> = emptyList(),
        tags: List<String> = emptyList(),
        tagIds: List<Long> = emptyList(),
        musicId: Long? = null,
        title: String? = null,
        status: String = "published",
    ): Result<Post>

    suspend fun getPostDetail(id: Long): Result<Post>

    suspend fun deletePost(id: Long): Result<Unit>

    suspend fun updatePost(
        id: Long,
        content: String,
        images: List<String> = emptyList(),
        media: List<com.fox.music.core.model.social.PostMedia>? = null,
        tags: List<String>? = null,
        tagIds: List<Long>? = null,
        musicId: Long? = null,
        title: String? = null,
        status: String? = null,
    ): Result<Post>

    suspend fun togglePostLike(id: Long): Result<Unit>

    suspend fun getPostLikes(
        id: Long,
        page: Int = 1,
        limit: Int = 20,
    ): Result<PagedData<com.fox.music.core.model.user.User>>

    suspend fun togglePostFavorite(id: Long): Result<Unit>

    suspend fun getPostComments(id: Long, page: Int = 1, limit: Int = 20): Result<PagedData<Comment>>

    suspend fun getPostCommentReplies(
        commentId: Long,
        page: Int = 1,
        limit: Int = 20,
    ): Result<PagedData<Comment>>

    suspend fun postComment(postId: Long, content: String, parentId: Long? = null): Result<Comment>

    suspend fun deletePostComment(id: Long): Result<Unit>

    suspend fun getFavorites(
        type: String? = null,
        page: Int = 1,
        limit: Int = 20
    ): Result<PagedData<Favorite>>

    suspend fun getNotifications(
        page: Int = 1,
        limit: Int = 20,
        type: String? = null,
    ): Result<PagedData<Notification>>

    suspend fun getUnreadNotificationCount(): Result<Int>

    suspend fun markNotificationRead(notificationIds: List<Long>): Result<Unit>

    suspend fun markAllNotificationsRead(): Result<Unit>

    suspend fun deleteNotifications(notificationIds: List<Long>): Result<Unit>
}
