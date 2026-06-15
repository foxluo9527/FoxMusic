package com.fox.music.core.domain.repository

import android.net.Uri
import com.fox.music.core.model.social.PostDraft

interface PostDraftRepository {

    suspend fun loadDraft(): PostDraft?

    suspend fun saveDraft(content: String, localImagePaths: List<String>)

    suspend fun persistImage(uri: Uri): Uri

    suspend fun clearDraft()
}
