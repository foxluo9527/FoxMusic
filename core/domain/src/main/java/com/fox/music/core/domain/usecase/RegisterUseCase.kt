package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AuthRepository
import com.fox.music.core.domain.repository.LoginResult
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(username: String, password: String, email: String): Result<LoginResult> =
        authRepository.register(username, password, email)
}
