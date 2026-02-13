package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.model.HotKeyword
import javax.inject.Inject

class GetHotKeywordsUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(type: String? = null, limit: Int = 10): Result<List<HotKeyword>> =
        searchRepository.getHotKeywords(type, limit)
}
