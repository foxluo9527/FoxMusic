package com.fox.music.feature.social

import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Tag
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * Bridges select-music / select-tag screens back to [CreatePostViewModel]
 * without relying on NavController SavedStateHandle route lookups.
 */
@ActivityRetainedScoped
class CreatePostSelectionStore @Inject constructor() {

    private val _music = MutableSharedFlow<Music>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val music: SharedFlow<Music> = _music.asSharedFlow()

    private val _tag = MutableSharedFlow<Tag>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val tag: SharedFlow<Tag> = _tag.asSharedFlow()

    fun selectMusic(music: Music) {
        _music.tryEmit(music)
    }

    fun selectTag(tag: Tag) {
        _tag.tryEmit(tag)
    }
}
