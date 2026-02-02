package com.fox.music.core.domain.usecase

import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.model.SearchHistory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSearchHistoryUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    operator fun invoke(limit: Int = 20): Flow<List<SearchHistory>> =
        searchRepository.getSearchHistory(limit)
}
