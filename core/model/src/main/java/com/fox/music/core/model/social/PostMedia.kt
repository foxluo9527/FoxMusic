package com.fox.music.core.model.social

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PostMediaType {
    @SerialName("image")
    IMAGE,
    @SerialName("video")
    VIDEO,
    @SerialName("live")
    LIVE,
}

@Serializable
data class PostMedia(
    val type: PostMediaType = PostMediaType.IMAGE,
    val url: String,
    @SerialName("video_url")
    val videoUrl: String? = null,
    @SerialName("cover_url")
    val coverUrl: String? = null,
) {
    val imageCoverUrl: String?
        get() = coverUrl?.takeIf { it.isNotBlank() && !isLikelyVideoUrl(it) }

    val displayUrl: String
        get() = when (type) {
            PostMediaType.VIDEO -> imageCoverUrl ?: url.takeIf { !isLikelyVideoUrl(it) }.orEmpty()
            else -> url
        }

    val playbackUrl: String?
        get() = when (type) {
            PostMediaType.VIDEO -> videoUrl?.takeIf { it.isNotBlank() } ?: url
            PostMediaType.LIVE -> videoUrl?.takeIf { it.isNotBlank() }
            PostMediaType.IMAGE -> null
        }

    /** 用于探测宽高比：视频取播放地址，图片取展示地址。 */
    val aspectRatioSourceUrl: String
        get() = when (type) {
            PostMediaType.VIDEO -> playbackUrl ?: url
            else -> displayUrl.ifBlank { url }
        }

    companion object {
        fun isLikelyVideoUrl(url: String): Boolean {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return path.endsWith(".mp4") ||
                path.endsWith(".mov") ||
                path.endsWith(".m4v") ||
                path.endsWith(".webm") ||
                path.endsWith(".mkv") ||
                path.endsWith(".3gp")
        }
    }
}
