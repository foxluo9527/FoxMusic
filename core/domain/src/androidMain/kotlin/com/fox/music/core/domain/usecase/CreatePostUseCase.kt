package com.fox.music.core.domain.usecase

import android.net.Uri
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PostDraftRepository
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.PostMediaType

data class LocalPostMedia(
    val type: PostMediaType,
    val uri: Uri,
    val videoUri: Uri? = null,
)

class CreatePostUseCase constructor(
    private val socialRepository: SocialRepository,
    private val uploadRepository: UploadRepository,
    private val postDraftRepository: PostDraftRepository,
) {
    suspend fun uploadMedia(
        localMedia: List<LocalPostMedia>,
        remoteMedia: List<PostMedia> = emptyList(),
    ): Result<List<PostMedia>> {
        val uploaded = mutableListOf<PostMedia>()
        uploaded.addAll(remoteMedia)
        for (item in localMedia) {
            when (item.type) {
                PostMediaType.IMAGE -> {
                    when (val uploadResult = uploadRepository.uploadImage(item.uri, compress = true)) {
                        is Result.Success -> uploaded.add(
                            PostMedia(type = PostMediaType.IMAGE, url = uploadResult.data),
                        )
                        is Result.Error -> return Result.Error(uploadResult.exception)
                        is Result.Loading -> Unit
                    }
                }
                PostMediaType.VIDEO -> {
                    when (val uploadResult = uploadRepository.uploadVideo(item.uri)) {
                        is Result.Success -> uploaded.add(
                            PostMedia(
                                type = PostMediaType.VIDEO,
                                url = uploadResult.data,
                                videoUrl = uploadResult.data,
                            ),
                        )
                        is Result.Error -> return Result.Error(uploadResult.exception)
                        is Result.Loading -> Unit
                    }
                }
                PostMediaType.LIVE -> {
                    val still = when (val r = uploadRepository.uploadImage(item.uri, compress = true)) {
                        is Result.Success -> r.data
                        is Result.Error -> return Result.Error(r.exception)
                        is Result.Loading -> continue
                    }
                    val videoUri = item.videoUri
                        ?: return Result.Error(IllegalArgumentException("Live 图缺少视频"))
                    // Live 动图片段可能非标准 video MIME，走通用文件上传
                    val motion = when (
                        val r = uploadRepository.uploadFile(
                            uri = videoUri,
                            fileName = "live_${System.currentTimeMillis()}.mp4",
                        )
                    ) {
                        is Result.Success -> r.data
                        is Result.Error -> return Result.Error(r.exception)
                        is Result.Loading -> continue
                    }
                    uploaded.add(
                        PostMedia(
                            type = PostMediaType.LIVE,
                            url = still,
                            videoUrl = motion,
                        ),
                    )
                }
            }
        }
        return Result.Success(uploaded)
    }

    suspend operator fun invoke(
        content: String,
        localImageUris: List<Uri> = emptyList(),
        localMedia: List<LocalPostMedia> = emptyList(),
        remoteMedia: List<PostMedia> = emptyList(),
        tags: List<String> = emptyList(),
        tagIds: List<Long> = emptyList(),
        musicId: Long? = null,
        title: String? = null,
    ): Result<Post> {
        val toUpload = if (localMedia.isNotEmpty()) {
            localMedia
        } else {
            localImageUris.map { LocalPostMedia(PostMediaType.IMAGE, it) }
        }
        val mediaResult = uploadMedia(toUpload, remoteMedia)
        val uploaded = when (mediaResult) {
            is Result.Success -> mediaResult.data
            is Result.Error -> return mediaResult
            is Result.Loading -> return Result.Loading
        }

        return when (
            val result = socialRepository.createPost(
                content = content,
                media = uploaded,
                tags = tags,
                tagIds = tagIds,
                musicId = musicId,
                title = title,
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
