package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.MusicFeedbackRepository

class SubmitMusicFeedbackUseCase constructor(
    private val musicFeedbackRepository: MusicFeedbackRepository,
) {
    suspend operator fun invoke(
        musicId: Long,
        content: String? = null,
    ): Result<Unit> = musicFeedbackRepository.submitFeedback(
        musicId = musicId,
        content = content,
    )
}
