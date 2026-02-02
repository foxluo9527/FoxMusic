package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toComment
import com.fox.music.core.data.mapper.toFavorite
import com.fox.music.core.data.mapper.toFriend
import com.fox.music.core.data.mapper.toFriendRequest
import com.fox.music.core.data.mapper.toNotification
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.data.mapper.toPost
import com.fox.music.core.data.mapper.toSearchedUser
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.Comment
import com.fox.music.core.model.Favorite
import com.fox.music.core.model.Friend
import com.fox.music.core.model.FriendRequest
import com.fox.music.core.model.Notification
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.Post
import com.fox.music.core.model.SearchedUser
import com.fox.music.core.network.api.SocialApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepositoryImpl @Inject constructor(
    private val socialApi: SocialApiService
) : SocialRepository {

    override suspend fun getFriends(): Result<List<Friend>> = suspendRunCatching {
        val response = socialApi.getFriends()
        val data = response.data
        if (response.isSuccess && data != null) {
            data.map { it.toFriend() }
        } else throw Exception(response.message)
    }

    override suspend fun getFriendRequests(): Result<List<FriendRequest>> = suspendRunCatching {
        val response = socialApi.getFriendRequests()
        val data = response.data
        if (response.isSuccess && data != null) {
            data.map { it.toFriendRequest() }
        } else throw Exception(response.message)
    }

    override suspend fun searchUsers(keyword: String): Result<List<SearchedUser>> =
        suspendRunCatching {
            val response = socialApi.searchUsers(keyword)
            val data = response.data
            if (response.isSuccess && data != null) {
                data.map { it.toSearchedUser() }
            } else throw Exception(response.message)
        }

    override suspend fun sendFriendRequest(userId: Long, message: String?): Result<Unit> =
        suspendRunCatching {
            val response = socialApi.sendFriendRequest(
                com.fox.music.core.network.model.FriendRequestBody(
                    friendId = userId,
                    message = message ?: ""
                )
            )
            if (response.isSuccess) Unit else throw Exception(response.message)
        }

    override suspend fun acceptFriendRequest(requestId: Long): Result<Unit> = suspendRunCatching {
        val response = socialApi.acceptFriendRequest(
            com.fox.music.core.network.model.AcceptFriendRequest(requestId = requestId)
        )
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun deleteFriend(friendId: Long): Result<Unit> = suspendRunCatching {
        val response = socialApi.deleteFriend(friendId)
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun setFriendRemark(friendId: Long, remark: String): Result<Unit> =
        suspendRunCatching {
            val response = socialApi.setFriendRemark(
                com.fox.music.core.network.model.SetRemarkRequest(
                    friendId = friendId,
                    remark = remark
                )
            )
            if (response.isSuccess) Unit else throw Exception(response.message)
        }

    override suspend fun getPosts(
        page: Int,
        limit: Int,
        keyword: String?
    ): Result<PagedData<Post>> = suspendRunCatching {
        val response = socialApi.getPosts(page, limit, keyword)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toPost() }
        } else throw Exception(response.message)
    }

    override suspend fun createPost(
        content: String,
        images: List<String>,
        musicId: Long?
    ): Result<Post> = suspendRunCatching {
        val response = socialApi.createPost(
            com.fox.music.core.network.model.CreatePostRequest(
                content = content,
                images = images.ifEmpty { null },
                musicId = musicId
            )
        )
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPost()
        } else throw Exception(response.message)
    }

    override suspend fun getPostDetail(id: Long): Result<Post> = suspendRunCatching {
        val response = socialApi.getPostDetail(id)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPost()
        } else throw Exception(response.message)
    }

    override suspend fun deletePost(id: Long): Result<Unit> = suspendRunCatching {
        val response = socialApi.deletePost(id)
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun togglePostLike(id: Long): Result<Unit> = suspendRunCatching {
        val response = socialApi.togglePostLike(id)
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun getPostComments(
        id: Long,
        page: Int,
        limit: Int
    ): Result<PagedData<Comment>> = suspendRunCatching {
        val response = socialApi.getPostComments(id, page, limit)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toComment() }
        } else throw Exception(response.message)
    }

    override suspend fun postComment(
        postId: Long,
        content: String,
        parentId: Long?
    ): Result<Comment> = suspendRunCatching {
        val response = socialApi.postComment(
            postId,
            com.fox.music.core.network.model.PostCommentRequest(
                musicId = null,
                content = content,
                parentId = parentId
            )
        )
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toComment()
        } else throw Exception(response.message)
    }

    override suspend fun getFavorites(
        type: String?,
        page: Int,
        limit: Int
    ): Result<PagedData<Favorite>> = suspendRunCatching {
        val response = socialApi.getFavorites(type, page, limit)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toFavorite() }
        } else throw Exception(response.message)
    }

    override suspend fun getNotifications(
        page: Int,
        limit: Int
    ): Result<PagedData<Notification>> = suspendRunCatching {
        val response = socialApi.getNotifications(page, limit)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toNotification() }
        } else throw Exception(response.message)
    }

    override suspend fun getUnreadNotificationCount(): Result<Int> = suspendRunCatching {
        val response = socialApi.getUnreadCount()
        val data = response.data
        if (response.isSuccess && data != null) {
            data.count
        } else throw Exception(response.message)
    }

    override suspend fun markNotificationRead(notificationIds: List<Long>): Result<Unit> =
        suspendRunCatching {
            val response = socialApi.markAsRead(
                com.fox.music.core.network.model.MarkReadRequest(ids = notificationIds)
            )
            if (response.isSuccess) Unit else throw Exception(response.message)
        }

    override suspend fun markAllNotificationsRead(): Result<Unit> = suspendRunCatching {
        val response = socialApi.markAllAsRead()
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun deleteNotifications(notificationIds: List<Long>): Result<Unit> =
        suspendRunCatching {
            val response = socialApi.deleteNotifications(
                com.fox.music.core.network.model.DeleteNotificationsRequest(ids = notificationIds)
            )
            if (response.isSuccess) Unit else throw Exception(response.message)
        }
}
