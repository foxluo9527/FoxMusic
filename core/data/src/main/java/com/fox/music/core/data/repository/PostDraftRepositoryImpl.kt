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

    override suspend fun saveDraft(content: String, localImagePaths: List<String>) {
        postDraftStorage.saveDraft(
            PostDraft(
                content = content,
                localImagePaths = localImagePaths,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun persistImage(uri: Uri): Uri = postDraftStorage.persistUri(uri)

    override suspend fun clearDraft() {
        postDraftStorage.clearDraft()
    }
}
