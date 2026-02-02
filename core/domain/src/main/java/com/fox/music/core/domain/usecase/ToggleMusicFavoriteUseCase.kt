package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.MusicRepository
import javax.inject.Inject

class ToggleMusicFavoriteUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(musicId: Long): Result<Unit> = musicRepository.toggleFavorite(musicId)
}
