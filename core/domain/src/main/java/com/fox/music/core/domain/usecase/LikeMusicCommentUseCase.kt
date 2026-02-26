package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.MusicRepository
import javax.inject.Inject

class LikeMusicCommentUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(commentId: Long): Result<Unit> = musicRepository.likeMusicComment(commentId)
}

