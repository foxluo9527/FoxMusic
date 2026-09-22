package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository

class DeletePostCommentUseCase constructor(
    private val socialRepository: SocialRepository,
) {
    suspend operator fun invoke(commentId: Long): Result<Unit> =
        socialRepository.deletePostComment(commentId)
}
