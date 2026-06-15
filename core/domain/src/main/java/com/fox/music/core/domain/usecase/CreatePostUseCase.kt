package com.fox.music.core.domain.usecase

import android.net.Uri
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PostDraftRepository
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.model.social.Post
import javax.inject.Inject

class CreatePostUseCase @Inject constructor(
    private val socialRepository: SocialRepository,
    private val uploadRepository: UploadRepository,
    private val postDraftRepository: PostDraftRepository,
) {
    suspend operator fun invoke(
        content: String,
        localImageUris: List<Uri>,
    ): Result<Post> {
        val imageUrls = mutableListOf<String>()
        for (uri in localImageUris) {
            when (val uploadResult = uploadRepository.uploadImage(uri, compress = true)) {
                is Result.Success -> imageUrls.add(uploadResult.data)
                is Result.Error -> return Result.Error(uploadResult.exception)
                is Result.Loading -> Unit
            }
        }

        return when (
            val result = socialRepository.createPost(
                content = content,
                images = imageUrls,
                status = "published",
            )
        ) {
            is Result.Success -> {
                postDraftRepository.clearDraft()
                result
            }
            is Result.Error -> result
            is Result.Loading -> result
        }
    }
}
