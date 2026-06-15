package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.social.Post
import javax.inject.Inject

class UpdatePostUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
) {
    suspend operator fun invoke(
        id: Long,
        content: String,
        images: List<String> = emptyList(),
        title: String? = null,
        status: String? = "published",
    ): Result<Post> = socialRepository.updatePost(id, content, images, title, status)
}
