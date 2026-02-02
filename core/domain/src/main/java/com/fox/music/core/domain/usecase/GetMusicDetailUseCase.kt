package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.model.MusicDetail
import javax.inject.Inject

class GetMusicDetailUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(id: Long): Result<MusicDetail> = musicRepository.getMusicDetail(id)
}
