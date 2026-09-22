package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AuthRepository
import com.fox.music.core.model.user.User

class GetProfileUseCase constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<User> = authRepository.getProfile()
}
