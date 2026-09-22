package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SearchRepository

class SaveSearchHistoryUseCase constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(keyword: String): Result<Unit> =
        searchRepository.saveSearchHistory(keyword)
}
