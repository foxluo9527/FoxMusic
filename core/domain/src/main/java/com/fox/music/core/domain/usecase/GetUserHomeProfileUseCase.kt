package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.UserRepository
import com.fox.music.core.model.user.UserHomeProfile
import javax.inject.Inject

class GetUserHomeProfileUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(userId: Long): Result<UserHomeProfile> =
        userRepository.getUserHomeProfile(userId)
}
