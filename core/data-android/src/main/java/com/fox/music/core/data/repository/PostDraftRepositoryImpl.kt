package com.fox.music.core.data.repository

import android.net.Uri
import com.fox.music.core.data.util.PostDraftStorage
import com.fox.music.core.domain.repository.PostDraftRepository
import com.fox.music.core.model.social.PostDraft
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostDraftRepositoryImpl @Inject constructor(
    private val postDraftStorage: PostDraftStorage,
) : PostDraftRepository {

    override suspend fun loadDraft(): PostDraft? = postDraftStorage.loadDraft()

    override suspend fun saveDraft(draft: PostDraft) {
        postDraftStorage.saveDraft(draft.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun persistImage(uri: Uri, extension: String): Uri =
        postDraftStorage.persistUri(uri, extension)

    override suspend fun persistCompressedImage(uri: Uri): Uri =
        postDraftStorage.persistCompressedImage(uri)

    override suspend fun persistCompressedVideo(uri: Uri): Uri =
        postDraftStorage.persistCompressedVideo(uri)

    override suspend fun clearDraft() {
        postDraftStorage.clearDraft()
    }

    override suspend fun clearDraftMetadata() {
        postDraftStorage.clearDraftMetadata()
    }
}
