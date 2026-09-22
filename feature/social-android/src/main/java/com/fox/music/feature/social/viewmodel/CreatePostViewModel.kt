package com.fox.music.feature.social.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.EnqueuePostPublishMedia
import com.fox.music.core.domain.repository.PostDraftRepository
import com.fox.music.core.domain.repository.PostPublishRepository
import com.fox.music.core.domain.usecase.GetPostDetailUseCase
import com.fox.music.core.domain.usecase.GetRecommendedPostTagsUseCase
import com.fox.music.core.model.music.Tag
import com.fox.music.core.model.social.DraftMediaItem
import com.fox.music.core.model.social.DraftTagItem
import com.fox.music.core.model.social.PostDraft
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.PostMediaType
import com.fox.music.feature.social.CreatePostSelectionStore
import com.fox.music.core.common.util.MotionPhotoExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreatePostMediaItem(
    val type: PostMediaType,
    val uri: Uri,
    val videoUri: Uri? = null,
    val remote: PostMedia? = null,
)

data class CreatePostTagItem(
    val tag: Tag,
    val checked: Boolean = true,
    val operatedAt: Long = 0L,
)

data class CreatePostState(
    val editingPostId: Long? = null,
    val title: String = "",
    val content: String = "",
    val mediaItems: List<CreatePostMediaItem> = emptyList(),
    val selectedTags: List<CreatePostTagItem> = emptyList(),
    val recommendedTags: List<Tag> = emptyList(),
    val musicId: Long? = null,
    val musicTitle: String? = null,
    val musicCover: String? = null,
    val musicArtist: String? = null,
    val musicIsFavorite: Boolean = false,
    val isPublishing: Boolean = false,
    val isAddingMedia: Boolean = false,
    val isLoadingDraft: Boolean = true,
    val publishSucceeded: Boolean = false,
) : UiState {
    val isEditMode: Boolean get() = editingPostId != null

    /** 选中在前，未选中在后；组内按最近操作时间置顶。 */
    val displayTags: List<CreatePostTagItem>
        get() = selectedTags.sortedWith(
            compareByDescending<CreatePostTagItem> { it.checked }
                .thenByDescending { it.operatedAt },
        )
}

sealed interface CreatePostIntent : UiIntent {
    data object LoadDraft : CreatePostIntent
    data class UpdateTitle(val title: String) : CreatePostIntent
    data class UpdateContent(val content: String) : CreatePostIntent
    data class AddMedia(val uri: Uri, val mimeType: String?) : CreatePostIntent
    data class RemoveMedia(val index: Int) : CreatePostIntent
    data class MoveMedia(val fromIndex: Int, val toIndex: Int) : CreatePostIntent
    data class SelectTag(val tag: Tag) : CreatePostIntent
    data class ToggleTag(val tag: Tag) : CreatePostIntent
    data object ClearMusic : CreatePostIntent
    data object RefreshRecommendedTags : CreatePostIntent
    data object Publish : CreatePostIntent
    data object SaveDraftNow : CreatePostIntent
}

sealed interface CreatePostEffect : UiEffect {
    data class ShowToast(val message: String) : CreatePostEffect
    data object Published : CreatePostEffect
    data object QueuedForPublish : CreatePostEffect
}

/** 编辑回填时，正文与标题相同视为「无正文」，避免历史发布逻辑把标题写入 content 后再次编辑时误填。 */
private fun resolveEditContent(title: String?, content: String): String {
    val titleText = title?.trim().orEmpty()
    val contentText = content.trim()
    if (contentText.isBlank()) return ""
    if (titleText.isNotBlank() && contentText == titleText) return ""
    return content
}

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val postDraftRepository: PostDraftRepository,
    private val postPublishRepository: PostPublishRepository,
    private val getPostDetailUseCase: GetPostDetailUseCase,
    private val getRecommendedPostTagsUseCase: GetRecommendedPostTagsUseCase,
    private val selectionStore: CreatePostSelectionStore,
) : MviViewModel<CreatePostState, CreatePostIntent, CreatePostEffect>(CreatePostState()) {

    private val editPostId: Long? = savedStateHandle.get<Long>("postId")?.takeIf { it > 0 }
    private var saveDraftJob: Job? = null
    private var recommendJob: Job? = null

    init {
        sendIntent(CreatePostIntent.LoadDraft)
        viewModelScope.launch {
            selectionStore.music.collect { music ->
                updateState {
                    copy(
                        musicId = music.id,
                        musicTitle = music.title,
                        musicCover = music.coverImage,
                        musicArtist = music.artists.joinToString("/") { it.name },
                        musicIsFavorite = music.isFavorite,
                    )
                }
                scheduleSaveDraft()
                scheduleRecommendTags()
            }
        }
        viewModelScope.launch {
            selectionStore.tag.collect { tag ->
                addSelectedTag(tag)
            }
        }
    }

    override fun handleIntent(intent: CreatePostIntent) {
        when (intent) {
            CreatePostIntent.LoadDraft -> loadInitial()
            is CreatePostIntent.UpdateTitle -> {
                updateState { copy(title = intent.title) }
                if (!currentState.isEditMode) scheduleSaveDraft()
                scheduleRecommendTags()
            }
            is CreatePostIntent.UpdateContent -> {
                updateState { copy(content = intent.content) }
                if (!currentState.isEditMode) scheduleSaveDraft()
                scheduleRecommendTags()
            }
            is CreatePostIntent.AddMedia -> addMedia(intent.uri, intent.mimeType)
            is CreatePostIntent.RemoveMedia -> removeMedia(intent.index)
            is CreatePostIntent.MoveMedia -> moveMedia(intent.fromIndex, intent.toIndex)
            is CreatePostIntent.SelectTag -> addSelectedTag(intent.tag)
            is CreatePostIntent.ToggleTag -> toggleSelectedTag(intent.tag)
            CreatePostIntent.ClearMusic -> {
                updateState {
                    copy(
                        musicId = null,
                        musicTitle = null,
                        musicCover = null,
                        musicArtist = null,
                        musicIsFavorite = false,
                    )
                }
                scheduleSaveDraft()
                scheduleRecommendTags()
            }
            CreatePostIntent.RefreshRecommendedTags -> scheduleRecommendTags(immediate = true)
            CreatePostIntent.Publish -> publish()
            CreatePostIntent.SaveDraftNow -> saveDraftNow()
        }
    }

    private fun loadInitial() {
        val postId = editPostId
        if (postId != null) {
            loadPostForEdit(postId)
        } else {
            loadDraft()
        }
    }

    private fun loadPostForEdit(postId: Long) {
        viewModelScope.launch {
            when (val result = getPostDetailUseCase(postId)) {
                is Result.Success -> {
                    val post = result.data
                    updateState {
                        copy(
                            editingPostId = postId,
                            title = post.title.orEmpty(),
                            content = resolveEditContent(post.title, post.content),
                            mediaItems = post.displayMedia.map {
                                CreatePostMediaItem(
                                    type = it.type,
                                    uri = Uri.parse(it.displayUrl),
                                    videoUri = it.videoUrl?.let(Uri::parse),
                                    remote = it,
                                )
                            },
                            selectedTags = post.tags.mapIndexed { index, tag ->
                                CreatePostTagItem(
                                    tag = tag,
                                    checked = true,
                                    operatedAt = (post.tags.size - index).toLong(),
                                )
                            },
                            musicId = post.music?.id,
                            musicTitle = post.music?.title,
                            musicCover = post.music?.coverImage,
                            musicArtist = post.music?.artists?.joinToString("/") { it.name },
                            musicIsFavorite = post.music?.isFavorite == true,
                            isLoadingDraft = false,
                        )
                    }
                    scheduleRecommendTags(immediate = true)
                }
                is Result.Error -> {
                    updateState { copy(isLoadingDraft = false) }
                    sendEffect(CreatePostEffect.ShowToast(result.exception.message ?: "加载失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun loadDraft() {
        viewModelScope.launch {
            val draft = postDraftRepository.loadDraft()
            if (draft != null) {
                val media = if (draft.localMedia.isNotEmpty()) {
                    draft.localMedia.map {
                        CreatePostMediaItem(
                            type = it.type,
                            uri = Uri.parse(it.localPath),
                            videoUri = it.videoLocalPath?.let(Uri::parse),
                        )
                    }
                } else {
                    draft.localImagePaths.map {
                        CreatePostMediaItem(type = PostMediaType.IMAGE, uri = Uri.parse(it))
                    }
                }
                updateState {
                    // Preserve in-progress selection if user returned from pickers before draft loaded.
                    val draftTags = draft.tagItems.map {
                        CreatePostTagItem(
                            tag = Tag(id = it.id, name = it.name),
                            checked = it.checked,
                            operatedAt = it.operatedAt,
                        )
                    }.ifEmpty {
                        draft.tags.mapIndexed { index, name ->
                            CreatePostTagItem(
                                tag = Tag(id = 0L, name = name),
                                checked = true,
                                operatedAt = (draft.tags.size - index).toLong(),
                            )
                        }
                    }
                    copy(
                        title = title.ifBlank { draft.title },
                        content = content.ifBlank { draft.content },
                        mediaItems = mediaItems.ifEmpty { media },
                        selectedTags = selectedTags.ifEmpty { draftTags },
                        musicId = musicId ?: draft.musicId,
                        musicTitle = musicTitle ?: draft.musicTitle,
                        musicCover = musicCover ?: draft.musicCover,
                        musicArtist = musicArtist ?: draft.musicArtist,
                        isLoadingDraft = false,
                    )
                }
                scheduleRecommendTags(immediate = true)
            } else {
                updateState { copy(isLoadingDraft = false) }
            }
        }
    }

    private fun addMedia(uri: Uri, mimeType: String?) {
        viewModelScope.launch {
            if (currentState.mediaItems.size >= 9) {
                sendEffect(CreatePostEffect.ShowToast("最多添加 9 个媒体"))
                return@launch
            }
            updateState { copy(isAddingMedia = true) }
            try {
                val isVideo = mimeType?.startsWith("video") == true
                val newItem = if (isVideo) {
                    val persisted = postDraftRepository.persistCompressedVideo(uri)
                    CreatePostMediaItem(
                        type = PostMediaType.VIDEO,
                        uri = persisted,
                        videoUri = persisted,
                    )
                } else {
                    val motion = MotionPhotoExtractor.extract(appContext, uri)
                    if (motion != null) {
                        val still = postDraftRepository.persistCompressedImage(motion.stillUri)
                        val video = postDraftRepository.persistCompressedVideo(motion.videoUri)
                        CreatePostMediaItem(
                            type = PostMediaType.LIVE,
                            uri = still,
                            videoUri = video,
                        )
                    } else {
                        val persisted = postDraftRepository.persistCompressedImage(uri)
                        CreatePostMediaItem(
                            type = PostMediaType.IMAGE,
                            uri = persisted,
                        )
                    }
                }
                updateState {
                    copy(mediaItems = mediaItems + newItem)
                }
                if (!currentState.isEditMode) scheduleSaveDraft()
            } catch (e: Exception) {
                sendEffect(
                    CreatePostEffect.ShowToast(
                        e.message?.takeIf { it.isNotBlank() } ?: "媒体处理失败",
                    ),
                )
            } finally {
                updateState { copy(isAddingMedia = false) }
            }
        }
    }

    private fun removeMedia(index: Int) {
        updateState {
            copy(mediaItems = mediaItems.filterIndexed { i, _ -> i != index })
        }
        if (!currentState.isEditMode) scheduleSaveDraft()
    }

    private fun moveMedia(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        updateState {
            val items = mediaItems.toMutableList()
            if (fromIndex !in items.indices || toIndex !in items.indices) {
                this
            } else {
                val item = items.removeAt(fromIndex)
                items.add(toIndex, item)
                copy(mediaItems = items)
            }
        }
        if (!currentState.isEditMode) scheduleSaveDraft()
    }

    private fun scheduleRecommendTags(immediate: Boolean = false) {
        recommendJob?.cancel()
        recommendJob = viewModelScope.launch {
            if (!immediate) delay(350)
            val state = currentState
            val content = listOfNotNull(state.title, state.content)
                .joinToString(" ")
                .trim()
            if (content.isBlank() && state.musicId == null) {
                updateState { copy(recommendedTags = emptyList()) }
                return@launch
            }
            when (
                val result = getRecommendedPostTagsUseCase(
                    content = content.ifBlank { null },
                    musicId = state.musicId,
                    limit = 20,
                )
            ) {
                is Result.Success -> updateState { copy(recommendedTags = result.data) }
                else -> updateState { copy(recommendedTags = emptyList()) }
            }
        }
    }

    private fun addSelectedTag(tag: Tag) {
        val normalized = tag.name.trim().removePrefix("#")
        if (normalized.isBlank()) return
        val now = System.currentTimeMillis()
        val existing = currentState.selectedTags.firstOrNull {
            it.tag.name.equals(normalized, ignoreCase = true)
        }
        if (existing != null) {
            if (!existing.checked) {
                updateState {
                    copy(
                        selectedTags = selectedTags.map {
                            if (it.tag.name.equals(normalized, ignoreCase = true)) {
                                it.copy(checked = true, operatedAt = now)
                            } else {
                                it
                            }
                        },
                    )
                }
                scheduleSaveDraft()
            }
            return
        }
        updateState {
            copy(
                selectedTags = selectedTags + CreatePostTagItem(
                    tag = tag.copy(name = normalized),
                    checked = true,
                    operatedAt = now,
                ),
            )
        }
        scheduleSaveDraft()
    }

    private fun toggleSelectedTag(tag: Tag) {
        val now = System.currentTimeMillis()
        updateState {
            copy(
                selectedTags = selectedTags.map {
                    if (it.tag.name.equals(tag.name, ignoreCase = true)) {
                        it.copy(checked = !it.checked, operatedAt = now)
                    } else {
                        it
                    }
                },
            )
        }
        scheduleSaveDraft()
    }

    private fun scheduleSaveDraft() {
        if (currentState.isEditMode) return
        saveDraftJob?.cancel()
        saveDraftJob = viewModelScope.launch {
            delay(500)
            saveDraftNow()
        }
    }

    private fun saveDraftNow() {
        if (currentState.isEditMode) return
        viewModelScope.launch {
            val state = currentState
            postDraftRepository.saveDraft(
                PostDraft(
                    title = state.title,
                    content = state.content,
                    localImagePaths = state.mediaItems
                        .filter { it.type == PostMediaType.IMAGE }
                        .map { it.uri.toString() },
                    localMedia = state.mediaItems.map {
                        DraftMediaItem(
                            localPath = it.uri.toString(),
                            type = it.type,
                            videoLocalPath = it.videoUri?.toString(),
                        )
                    },
                    tags = state.selectedTags.filter { it.checked }.map { it.tag.name },
                    tagItems = state.selectedTags.map {
                        DraftTagItem(
                            id = it.tag.id,
                            name = it.tag.name,
                            checked = it.checked,
                            operatedAt = it.operatedAt,
                        )
                    },
                    musicId = state.musicId,
                    musicTitle = state.musicTitle,
                    musicCover = state.musicCover,
                    musicArtist = state.musicArtist,
                ),
            )
        }
    }

    private fun publish() {
        val state = currentState
        if (state.content.isBlank() && state.title.isBlank() && state.mediaItems.isEmpty()) {
            sendEffect(CreatePostEffect.ShowToast("请填写内容或添加媒体"))
            return
        }
        viewModelScope.launch {
            updateState { copy(isPublishing = true) }
            val enqueueMedia = state.mediaItems.map { item ->
                val remote = item.remote
                if (remote != null) {
                    EnqueuePostPublishMedia(
                        type = item.type,
                        remoteUrl = remote.url,
                        remoteVideoUrl = remote.videoUrl,
                    )
                } else {
                    EnqueuePostPublishMedia(
                        type = item.type,
                        uri = item.uri,
                        videoUri = item.videoUri,
                    )
                }
            }
            val checkedTags = state.selectedTags.filter { it.checked }
            val existingTagIds = checkedTags.map { it.tag.id }.filter { it > 0L }
            val newTagNames = checkedTags.filter { it.tag.id <= 0L }.map { it.tag.name }
            val result = postPublishRepository.enqueuePublish(
                title = state.title.ifBlank { null },
                content = state.content.ifBlank { state.title },
                media = enqueueMedia,
                tagNames = newTagNames,
                tagIds = existingTagIds,
                musicId = state.musicId,
                editingPostId = state.editingPostId,
            )
            updateState { copy(isPublishing = false) }
            when (result) {
                is Result.Success -> {
                    if (!state.isEditMode) {
                        postDraftRepository.clearDraftMetadata()
                    }
                    updateState { copy(publishSucceeded = true) }
                    sendEffect(CreatePostEffect.QueuedForPublish)
                }
                is Result.Error -> sendEffect(
                    CreatePostEffect.ShowToast(result.exception.message ?: "加入队列失败"),
                )
                is Result.Loading -> Unit
            }
        }
    }

}
