package com.fox.music.core.domain.usecase

import android.net.Uri
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.UploadRepository
import javax.inject.Inject

class UploadImageUseCase @Inject constructor(
    private val uploadRepository: UploadRepository,
) {
    suspend operator fun invoke(uri: Uri): Result<String> = uploadRepository.uploadImage(uri)
}
