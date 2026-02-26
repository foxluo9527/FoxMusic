package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.model.Comment
import com.fox.music.core.model.PagedData
import javax.inject.Inject

class GetCommentRepliesUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(
        commentId: Long,
        page: Int = 1,
        limit: Int = 20
    ): Result<PagedData<Comment>> = musicRepository.getCommentReplies(commentId, page, limit)
}

