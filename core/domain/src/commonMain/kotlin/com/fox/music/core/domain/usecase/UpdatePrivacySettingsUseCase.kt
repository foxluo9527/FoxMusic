package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.UserRepository
import com.fox.music.core.model.user.PrivacySettings

class UpdatePrivacySettingsUseCase constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(
        isPrivate: Boolean? = null,
        isSearchable: Boolean? = null,
    ): Result<PrivacySettings> = userRepository.updatePrivacySettings(isPrivate, isSearchable)
}
