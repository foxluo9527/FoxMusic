package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.social.Comment
import javax.inject.Inject

class PostCommentUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
) {
    suspend operator fun invoke(
        postId: Long,
        content: String,
        parentId: Long? = null,
    ): Result<Comment> = socialRepository.postComment(postId, content, parentId)
}
