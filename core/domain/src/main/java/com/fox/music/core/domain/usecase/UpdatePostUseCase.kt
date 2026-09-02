package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.social.PostMedia
import javax.inject.Inject

class UpdatePostUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
) {
    suspend operator fun invoke(
        id: Long,
        content: String,
        images: List<String> = emptyList(),
        media: List<PostMedia>? = null,
        tags: List<String>? = null,
        tagIds: List<Long>? = null,
        musicId: Long? = null,
        title: String? = null,
        status: String? = "published",
    ): Result<Post> = socialRepository.updatePost(
        id = id,
        content = content,
        images = images,
        media = media,
        tags = tags,
        tagIds = tagIds,
        musicId = musicId,
        title = title,
        status = status,
    )
}
