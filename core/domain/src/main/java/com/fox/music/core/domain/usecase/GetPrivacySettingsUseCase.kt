package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.UserRepository
import com.fox.music.core.model.user.PrivacySettings
import javax.inject.Inject

class GetPrivacySettingsUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(): Result<PrivacySettings> = userRepository.getPrivacySettings()
}
