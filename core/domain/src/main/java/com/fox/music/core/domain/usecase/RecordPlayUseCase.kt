package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.MusicRepository
import javax.inject.Inject

class RecordPlayUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(
        musicId: Long,
        durationSeconds: Int? = null,
        progressPercent: Int? = null
    ): Result<Unit> = musicRepository.recordPlay(musicId, durationSeconds, progressPercent)
}
