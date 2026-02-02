package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SearchRepository
import javax.inject.Inject

class GetHotKeywordsUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(type: String? = null, limit: Int = 10): Result<List<String>> =
        searchRepository.getHotKeywords(type, limit)
}
