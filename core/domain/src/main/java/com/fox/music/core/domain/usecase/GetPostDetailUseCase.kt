package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.social.Post
import javax.inject.Inject

class GetPostDetailUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
) {
    suspend operator fun invoke(postId: Long): Result<Post> =
        socialRepository.getPostDetail(postId)
}
