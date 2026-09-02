package com.fox.music.core.domain.usecase

import android.net.Uri
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.domain.repository.UserRepository
import com.fox.music.core.model.user.UserHomeProfile
import javax.inject.Inject

class UpdateHomepageBackgroundUseCase @Inject constructor(
    private val uploadRepository: UploadRepository,
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(uri: Uri): Result<UserHomeProfile> {
        return when (val upload = uploadRepository.uploadImage(uri)) {
            is Result.Success -> userRepository.updateHomepageBackground(upload.data)
            is Result.Error -> upload
            is Result.Loading -> Result.Error(Exception("上传中"))
        }
    }
}
