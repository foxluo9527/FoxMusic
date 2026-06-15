package com.fox.music.core.data.util

import android.content.Context
import android.net.Uri
import com.fox.music.core.model.social.PostDraft
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostDraftStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val draftDir: File
        get() = File(context.filesDir, "post_draft").apply { mkdirs() }

    private val mediaDir: File
        get() = File(context.filesDir, "post_draft_media").apply { mkdirs() }

    private val draftFile: File
        get() = File(draftDir, "draft.json")

    suspend fun loadDraft(): PostDraft? = withContext(Dispatchers.IO) {
        if (!draftFile.exists()) return@withContext null
        runCatching {
            json.decodeFromString<PostDraft>(draftFile.readText())
        }.getOrNull()
    }

    suspend fun saveDraft(draft: PostDraft) = withContext(Dispatchers.IO) {
        draftDir.mkdirs()
        draftFile.writeText(json.encodeToString(PostDraft.serializer(), draft))
    }

    suspend fun clearDraft() = withContext(Dispatchers.IO) {
        if (draftFile.exists()) {
            draftFile.delete()
        }
        mediaDir.listFiles()?.forEach { it.delete() }
    }

    fun persistUri(uri: Uri, extension: String = "jpg"): Uri {
        if (uri.scheme == "file") {
            val path = uri.path ?: return uri
            if (path.contains("post_draft_media")) return uri
        }
        val safeExtension = extension.trim('.').ifBlank { "jpg" }
        val target = File(mediaDir, "${UUID.randomUUID()}.$safeExtension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("无法读取图片文件")
        return Uri.fromFile(target)
    }

    fun removeImage(path: String) {
        val file = File(path.removePrefix("file://"))
        if (file.exists() && file.canonicalPath.contains("post_draft_media")) {
            file.delete()
        }
    }
}
