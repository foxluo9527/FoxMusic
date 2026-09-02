package com.fox.music.core.domain.repository

import android.net.Uri
import com.fox.music.core.model.social.DraftMediaItem
import com.fox.music.core.model.social.PostDraft

interface PostDraftRepository {

    suspend fun loadDraft(): PostDraft?

    suspend fun saveDraft(draft: PostDraft)

    suspend fun persistImage(uri: Uri, extension: String = "jpg"): Uri

    /** 压缩后写入草稿目录（≤2MB JPEG） */
    suspend fun persistCompressedImage(uri: Uri): Uri

    /** 1080p 以内压缩后写入草稿目录 */
    suspend fun persistCompressedVideo(uri: Uri): Uri

    suspend fun clearDraft()

    /** 清除草稿 JSON，不删除本地媒体（后台发布任务仍需要读取）。 */
    suspend fun clearDraftMetadata()
}
