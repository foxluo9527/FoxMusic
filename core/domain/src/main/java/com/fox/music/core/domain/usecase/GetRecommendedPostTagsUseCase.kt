package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.music.Tag
import javax.inject.Inject

class GetRecommendedPostTagsUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
) {
    suspend operator fun invoke(
        content: String? = null,
        musicId: Long? = null,
        limit: Int = 20,
    ): Result<List<Tag>> = socialRepository.getRecommendedPostTags(content, musicId, limit)
}
