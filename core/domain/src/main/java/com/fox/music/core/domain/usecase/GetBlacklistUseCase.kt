package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.UserRepository
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.user.BlacklistUser
import javax.inject.Inject

class GetBlacklistUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(page: Int = 1, limit: Int = 50): Result<PagedData<BlacklistUser>> =
        userRepository.getBlacklist(page, limit)
}
