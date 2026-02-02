package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.Comment
import com.fox.music.core.model.Music
import com.fox.music.core.model.MusicDetail
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.PlayHistory
import com.fox.music.core.model.Tag

interface MusicRepository {

    suspend fun getMusicList(
        page: Int = 1,
        limit: Int = 20,
        keyword: String? = null,
        tagId: Long? = null,
        sort: String? = null
    ): Result<PagedData<Music>>

    suspend fun getMusicDetail(id: Long): Result<MusicDetail>

    suspend fun toggleFavorite(id: Long): Result<Unit>

    suspend fun recordPlay(id: Long, durationSeconds: Int? = null, progressPercent: Int? = null): Result<Unit>

    suspend fun getMusicComments(
        musicId: Long,
        page: Int = 1,
        limit: Int = 20
    ): Result<PagedData<Comment>>

    suspend fun postComment(musicId: Long, content: String, parentId: Long? = null): Result<Comment>

    suspend fun deleteComment(id: Long): Result<Unit>

    suspend fun getCommentReplies(
        commentId: Long,
        page: Int = 1,
        limit: Int = 20
    ): Result<PagedData<Comment>>

    suspend fun getPlayHistory(page: Int = 1, limit: Int = 20): Result<PagedData<PlayHistory>>

    suspend fun deletePlayHistory(musicIds: List<Long>): Result<Unit>

    suspend fun getMusicTags(): Result<List<Tag>>
}
