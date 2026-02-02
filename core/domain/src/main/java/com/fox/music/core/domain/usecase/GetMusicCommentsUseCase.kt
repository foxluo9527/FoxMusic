package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.model.Comment
import com.fox.music.core.model.PagedData
import javax.inject.Inject

class GetMusicCommentsUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(
        musicId: Long,
        page: Int = 1,
        limit: Int = 20
    ): Result<PagedData<Comment>> = musicRepository.getMusicComments(musicId, page, limit)
}
