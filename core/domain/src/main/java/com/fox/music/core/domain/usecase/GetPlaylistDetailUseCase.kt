package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.model.PlaylistDetail
import javax.inject.Inject

class GetPlaylistDetailUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(id: Long, page: Int = 1, limit: Int = 20): Result<PlaylistDetail> =
        playlistRepository.getPlaylistDetail(id, page, limit)
}
