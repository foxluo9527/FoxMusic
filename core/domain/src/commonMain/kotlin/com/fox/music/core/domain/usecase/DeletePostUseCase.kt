package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository

class DeletePostUseCase constructor(
    private val socialRepository: SocialRepository,
) {
    suspend operator fun invoke(postId: Long): Result<Unit> =
        socialRepository.deletePost(postId)
}
