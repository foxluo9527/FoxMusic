package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toComment
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.data.mapper.toPlayHistory
import com.fox.music.core.data.mapper.toTag
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.model.Comment
import com.fox.music.core.model.Music
import com.fox.music.core.model.MusicDetail
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.PlayHistory
import com.fox.music.core.model.Tag
import com.fox.music.core.network.api.MusicApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val musicApi: MusicApiService
) : MusicRepository {

    override suspend fun getMusicList(
        page: Int,
        limit: Int,
        keyword: String?,
        tagId: Long?,
        sort: String?
    ): Result<PagedData<Music>> = suspendRunCatching {
        val response = musicApi.getMusicList(page, limit, keyword, tagId, sort)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toMusic() }
        } else throw Exception(response.message)
    }

    override suspend fun getMusicDetail(id: Long): Result<MusicDetail> = suspendRunCatching {
        val response = musicApi.getMusicDetail(id)
        val data = response.data
        if (response.isSuccess && data != null) {
            MusicDetail(music = data.toMusic(), relatedMusics = emptyList())
        } else throw Exception(response.message)
    }

    override suspend fun toggleFavorite(id: Long): Result<Unit> = suspendRunCatching {
        val response = musicApi.toggleFavorite(id)
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun recordPlay(
        id: Long,
        durationSeconds: Int?,
        progressPercent: Int?
    ): Result<Unit> = suspendRunCatching {
        val response = musicApi.recordPlay(
            id,
            com.fox.music.core.network.model.PlayRecordRequest(
                duration = durationSeconds,
                progress = progressPercent
            )
        )
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun getMusicComments(
        musicId: Long,
        page: Int,
        limit: Int
    ): Result<PagedData<Comment>> = suspendRunCatching {
        val response = musicApi.getMusicComments(musicId, page, limit)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toComment() }
        } else throw Exception(response.message)
    }

    override suspend fun postComment(
        musicId: Long,
        content: String,
        parentId: Long?
    ): Result<Comment> = suspendRunCatching {
        val response = musicApi.postComment(
            com.fox.music.core.network.model.PostCommentRequest(
                musicId = musicId,
                content = content,
                parentId = parentId
            )
        )
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toComment()
        } else throw Exception(response.message)
    }

    override suspend fun deleteComment(id: Long): Result<Unit> = suspendRunCatching {
        val response = musicApi.deleteComment(id)
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun getCommentReplies(
        commentId: Long,
        page: Int,
        limit: Int
    ): Result<PagedData<Comment>> = suspendRunCatching {
        val response = musicApi.getCommentReplies(commentId, page, limit)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toComment() }
        } else throw Exception(response.message)
    }

    override suspend fun getPlayHistory(page: Int, limit: Int): Result<PagedData<PlayHistory>> =
        suspendRunCatching {
            val response = musicApi.getPlayHistory(page, limit)
            val data = response.data
            if (response.isSuccess && data != null) {
                data.toPagedData { it.toPlayHistory() }
            } else throw Exception(response.message)
        }

    override suspend fun deletePlayHistory(musicIds: List<Long>): Result<Unit> = suspendRunCatching {
        val response = musicApi.deletePlayHistory(
            com.fox.music.core.network.model.DeleteHistoryRequest(musicIds = musicIds)
        )
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun getMusicTags(): Result<List<Tag>> = suspendRunCatching {
        val response = musicApi.getMusicTags()
        val data = response.data
        if (response.isSuccess && data != null) {
            data.map { it.toTag() }
        } else throw Exception(response.message)
    }
}
