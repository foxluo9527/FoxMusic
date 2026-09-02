package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.UserRepository
import javax.inject.Inject

class UnblockUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(userId: Long): Result<Unit> = userRepository.unblockUser(userId)
}
