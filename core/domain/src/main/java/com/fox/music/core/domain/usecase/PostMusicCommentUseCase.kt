package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.model.Comment
import javax.inject.Inject

class PostMusicCommentUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(
        musicId: Long,
        content: String,
        parentId: Long? = null
    ): Result<Comment> = musicRepository.postComment(musicId, content, parentId)
}
