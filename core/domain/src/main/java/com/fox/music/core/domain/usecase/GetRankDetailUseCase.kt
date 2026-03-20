package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.model.music.PlaylistDetail
import javax.inject.Inject

class GetRankDetailUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(id: Long, page: Int = 1, limit: Int = 20): Result<PlaylistDetail> =
        playlistRepository.getRankDetail(id, page, limit)
}
