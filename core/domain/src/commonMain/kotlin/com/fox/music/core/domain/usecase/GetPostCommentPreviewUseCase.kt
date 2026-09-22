package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.social.Comment

class GetPostCommentPreviewUseCase constructor(
    private val socialRepository: SocialRepository,
) {
    suspend operator fun invoke(postId: Long): Result<Comment?> {
        return when (val result = socialRepository.getPostComments(postId, page = 1, limit = 1)) {
            is Result.Success -> {
                val comment = result.data.list.firstOrNull() ?: return Result.Success(null)
                if (comment.replies.isNotEmpty() || comment.replyCount == 0) {
                    return Result.Success(comment)
                }
                when (val repliesResult = socialRepository.getPostCommentReplies(comment.id, page = 1, limit = 1)) {
                    is Result.Success -> {
                        val firstReply = repliesResult.data.list.firstOrNull()
                        Result.Success(
                            if (firstReply != null) comment.copy(replies = listOf(firstReply)) else comment,
                        )
                    }
                    else -> Result.Success(comment)
                }
            }
            is Result.Error -> Result.Error(result.exception)
            is Result.Loading -> Result.Loading
        }
    }
}
