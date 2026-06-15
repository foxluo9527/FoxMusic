package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.social.Post
import javax.inject.Inject

class GetRecentPostsUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
) {
    suspend operator fun invoke(limit: Int = 3): Result<List<Post>> {
        return when (val result = socialRepository.getPosts(page = 1, limit = limit, sort = "latest")) {
            is Result.Success -> Result.Success(result.data.list)
            is Result.Error -> Result.Error(result.exception)
            is Result.Loading -> Result.Loading
        }
    }
}
