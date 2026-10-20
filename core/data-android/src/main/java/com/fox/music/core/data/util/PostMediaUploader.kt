package com.fox.music.core.data.util

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.PostMediaType

class PostMediaUploader(
    private val context: Context,
    private val uploadRepository: UploadRepository,
) {

    suspend fun upload(
        type: PostMediaType,
        uri: Uri,
        videoUri: Uri? = null,
        fileName: String? = null,
    ): Result<PostMedia> {
        return when (type) {
            PostMediaType.IMAGE -> {
                when (val result = uploadRepository.uploadImage(uri, compress = true, fileName = fileName)) {
                    is Result.Success -> Result.Success(
                        PostMedia(type = PostMediaType.IMAGE, url = result.data),
                    )
                    is Result.Error -> result
                    is Result.Loading -> Result.Loading
                }
            }
            PostMediaType.VIDEO -> {
                val videoName = fileName?.takeIf { it.isNotBlank() }
                    ?: "post_video_${System.currentTimeMillis()}.mp4"
                when (val result = uploadRepository.uploadVideo(uri, fileName = videoName)) {
                    is Result.Success -> Result.Success(
                        PostMedia(
                            type = PostMediaType.VIDEO,
                            url = result.data,
                            videoUrl = result.data,
                        ),
                    )
                    is Result.Error -> result
                    is Result.Loading -> Result.Loading
                }
            }
            PostMediaType.LIVE -> {
                val still = when (val r = uploadRepository.uploadImage(uri, compress = true)) {
                    is Result.Success -> r.data
                    is Result.Error -> return r
                    is Result.Loading -> return Result.Loading
                }
                val motionUri = videoUri
                    ?: return Result.Error(IllegalArgumentException("Live 图缺少视频"))
                val motion = when (
                    val r = uploadRepository.uploadFile(
                        uri = motionUri,
                        fileName = "live_${System.currentTimeMillis()}.mp4",
                    )
                ) {
                    is Result.Success -> r.data
                    is Result.Error -> return r
                    is Result.Loading -> return Result.Loading
                }
                Result.Success(
                    PostMedia(
                        type = PostMediaType.LIVE,
                        url = still,
                        videoUrl = motion,
                    ),
                )
            }
        }
    }

    companion object {
        fun resolveVideoMimeType(context: Context, uri: Uri, fileName: String?): String {
            context.contentResolver.getType(uri)
                ?.takeIf { it.startsWith("video/") }
                ?.let { return it }
            val name = fileName?.lowercase().orEmpty()
                .ifBlank { uri.lastPathSegment?.lowercase().orEmpty() }
            when {
                name.endsWith(".mp4") || name.endsWith(".m4v") -> return "video/mp4"
                name.endsWith(".webm") -> return "video/webm"
                name.endsWith(".mov") -> return "video/quicktime"
                name.endsWith(".mkv") -> return "video/x-matroska"
                name.endsWith(".3gp") -> return "video/3gpp"
            }
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(name))
                ?.takeIf { it.startsWith("video/") }
                ?.let { return it }
            return "video/mp4"
        }
    }
}
