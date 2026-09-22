package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.MusicRepository

data class MusicCommentNavigation(
    val musicId: Long,
    val commentId: Long,
)

class ResolveMusicCommentNavigationUseCase constructor(
    private val musicRepository: MusicRepository,
) {
    suspend operator fun invoke(commentId: Long): Result<MusicCommentNavigation> {
        when (val result = musicRepository.getCommentReplies(commentId, page = 1, limit = 20)) {
            is Result.Success -> {
                val musicId = result.data.list.firstNotNullOfOrNull { it.musicId }
                    ?: return Result.Error(Exception("无法定位音乐评论"))
                return Result.Success(
                    MusicCommentNavigation(
                        musicId = musicId,
                        commentId = commentId,
                    )
                )
            }
            is Result.Error -> return result
            is Result.Loading -> return Result.Error(Exception("加载中"))
        }
    }
}
