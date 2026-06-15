package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import javax.inject.Inject

class TogglePostFavoriteUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
) {
    suspend operator fun invoke(postId: Long): Result<Unit> =
        socialRepository.togglePostFavorite(postId)
}
