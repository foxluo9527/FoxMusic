package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AuthRepository
import com.fox.music.core.model.user.User
import javax.inject.Inject

class UpdateProfileUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        nickname: String? = null,
        avatar: String? = null,
        signature: String? = null,
        email: String? = null,
    ): Result<User> = authRepository.updateProfile(
        nickname = nickname,
        avatar = avatar,
        signature = signature,
        email = email,
    )
}
