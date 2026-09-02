package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.music.Tag
import javax.inject.Inject

class GetPostTagsUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
) {
    suspend operator fun invoke(limit: Int = 30, keyword: String? = null): Result<List<Tag>> =
        socialRepository.getPostTags(limit, keyword)
}
