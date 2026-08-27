package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import javax.inject.Inject

class DeletePostCommentUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
) {
    suspend operator fun invoke(commentId: Long): Result<Unit> =
        socialRepository.deletePostComment(commentId)
}
