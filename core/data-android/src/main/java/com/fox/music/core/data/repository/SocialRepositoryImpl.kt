package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toComment
import com.fox.music.core.data.mapper.toFavorite
import com.fox.music.core.data.mapper.toFriend
import com.fox.music.core.data.mapper.toFriendRequest
import com.fox.music.core.data.mapper.toNotification
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.data.mapper.toPostLikerUser
import com.fox.music.core.data.mapper.toPost
import com.fox.music.core.data.mapper.toDto
import com.fox.music.core.data.mapper.toSearchedUser
import com.fox.music.core.data.mapper.toTag
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.music.Favorite
import com.fox.music.core.model.chat.Friend
import com.fox.music.core.model.chat.FriendRequest
import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.NotificationType
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.chat.SearchedUser
import com.fox.music.core.database.dao.ConversationDao
import com.fox.music.core.network.api.SocialApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepositoryImpl @Inject constructor(
    private val socialApi: SocialApiService,
    private val conversationDao: ConversationDao,
    private val userPreferencesRepository: UserPreferencesRepository,
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

    override suspend fun sendFriendRequest(
        userId: Long,
        message: String,
        mark: String?,
    ): Result<Unit> = suspendRunCatching {
        val response = socialApi.sendFriendRequest(
            com.fox.music.core.network.model.FriendRequestBody(
                friendId = userId,
                message = message,
                mark = mark ?: "",
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
            if (response.isSuccess) {
                val ownerId = userPreferencesRepository.userPreferences.first().userId
                if (ownerId != null) {
                    // 同步本地会话备注，保证会话列表/聊天标题展示一致
                    conversationDao.updatePeerMark(ownerId, friendId, remark.ifBlank { null })
                }
            } else {
                throw Exception(response.message)
            }
        }

    override suspend fun getPosts(
        page: Int,
        limit: Int,
        keyword: String?,
        sort: String,
        tag: String?,
        authorId: Long?,
    ): Result<PagedData<Post>> = suspendRunCatching {
        val response = socialApi.getPosts(page, limit, keyword, tag, sort, authorId)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toPost() }
        } else throw Exception(response.message)
    }

    override suspend fun getPostTags(limit: Int, keyword: String?): Result<List<com.fox.music.core.model.music.Tag>> =
        suspendRunCatching {
            val response = socialApi.getPostTags(limit, keyword)
            val data = response.data
            if (response.isSuccess && data != null) {
                data.map { it.toTag() }
            } else throw Exception(response.message)
        }

    override suspend fun getRecommendedPostTags(
        content: String?,
        musicId: Long?,
        limit: Int,
    ): Result<List<com.fox.music.core.model.music.Tag>> = suspendRunCatching {
        val response = socialApi.getRecommendedPostTags(content, musicId, limit)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.map { it.toTag() }
        } else throw Exception(response.message)
    }

    override suspend fun createPost(
        content: String,
        images: List<String>,
        media: List<com.fox.music.core.model.social.PostMedia>,
        tags: List<String>,
        tagIds: List<Long>,
        musicId: Long?,
        title: String?,
        status: String,
    ): Result<Post> = suspendRunCatching {
        val resolvedTitle = title?.takeIf { it.isNotBlank() }
            ?: content.lineSequence().firstOrNull()?.take(50)?.trim()?.takeIf { it.isNotBlank() }
            ?: "无标题"
        val mediaDtos = media.map { it.toDto() }.ifEmpty { null }
        val response = socialApi.createPost(
            com.fox.music.core.network.model.CreatePostRequest(
                title = resolvedTitle,
                content = content,
                images = if (mediaDtos == null) images.ifEmpty { null } else null,
                media = mediaDtos,
                tags = tags.ifEmpty { null },
                tagIds = tagIds.ifEmpty { null },
                musicId = musicId,
                status = status,
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

    override suspend fun updatePost(
        id: Long,
        content: String,
        images: List<String>,
        media: List<com.fox.music.core.model.social.PostMedia>?,
        tags: List<String>?,
        tagIds: List<Long>?,
        musicId: Long?,
        title: String?,
        status: String?,
    ): Result<Post> = suspendRunCatching {
        val resolvedTitle = title?.takeIf { it.isNotBlank() }
            ?: content.lineSequence().firstOrNull()?.take(50)?.trim()?.takeIf { it.isNotBlank() }
        val mediaDtos = media?.map { it.toDto() }
        val response = socialApi.updatePost(
            id,
            com.fox.music.core.network.model.UpdatePostRequest(
                title = resolvedTitle,
                content = content,
                images = if (mediaDtos == null) images.ifEmpty { null } else null,
                media = mediaDtos,
                tags = tags,
                tagIds = tagIds,
                musicId = musicId,
                status = status,
            ),
        )
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPost()
        } else throw Exception(response.message)
    }

    override suspend fun togglePostLike(id: Long): Result<Unit> = suspendRunCatching {
        val response = socialApi.togglePostLike(id)
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun getPostLikes(
        id: Long,
        page: Int,
        limit: Int,
    ): Result<PagedData<com.fox.music.core.model.user.User>> = suspendRunCatching {
        val response = socialApi.getPostLikes(id, page, limit)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toPostLikerUser() }
        } else throw Exception(response.message)
    }

    override suspend fun togglePostFavorite(id: Long): Result<Unit> = suspendRunCatching {
        val response = socialApi.togglePostFavorite(id)
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

    override suspend fun getPostCommentReplies(
        commentId: Long,
        page: Int,
        limit: Int,
    ): Result<PagedData<Comment>> = suspendRunCatching {
        val response = socialApi.getPostCommentReplies(commentId, page, limit)
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
            com.fox.music.core.network.model.SocialPostCommentRequest(
                content = content,
                parentId = parentId,
            ),
        )
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toComment()
        } else throw Exception(response.message)
    }

    override suspend fun deletePostComment(id: Long): Result<Unit> = suspendRunCatching {
        val response = socialApi.deletePostComment(id)
        if (!response.isSuccess) throw Exception(response.message)
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
        limit: Int,
        type: String?,
    ): Result<PagedData<Notification>> = suspendRunCatching {
        val response = socialApi.getNotifications(page, limit, type)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toNotification() }
                .let { paged ->
                    paged.copy(
                        list = paged.list.filter { it.type != NotificationType.MESSAGE },
                    )
                }
        } else throw Exception(response.message)
    }

    override suspend fun getUnreadNotificationCount(): Result<Int> = suspendRunCatching {
        when (val notifications = getNotifications(page = 1, limit = 100)) {
            is Result.Success -> notifications.data.list.count { !it.isRead }
            is Result.Error -> throw notifications.exception
            is Result.Loading -> 0
        }
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
