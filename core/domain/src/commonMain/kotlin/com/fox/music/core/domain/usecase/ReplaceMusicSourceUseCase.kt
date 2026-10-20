package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.MusicFeedbackRepository

class ReplaceMusicSourceUseCase constructor(
    private val musicFeedbackRepository: MusicFeedbackRepository,
) {
    suspend operator fun invoke(
        musicId: Long,
        url: String,
        referer: String? = null,
        duration: Long? = null,
    ): Result<Unit> = musicFeedbackRepository.replaceMusicSource(
        musicId = musicId,
        url = url,
        referer = referer,
        duration = duration,
    )
}
