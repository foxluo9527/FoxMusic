package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.model.music.ListeningStats

class GetListeningStatsUseCase constructor(
    private val musicRepository: MusicRepository,
) {
    suspend operator fun invoke(days: Int = 7): Result<ListeningStats> =
        musicRepository.getListeningStats(days)
}
