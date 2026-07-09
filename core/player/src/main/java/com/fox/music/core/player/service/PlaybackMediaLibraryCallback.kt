package com.fox.music.core.player.service

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.fox.music.core.player.automotive.AutomotiveMediaCatalog
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@OptIn(UnstableApi::class)
class PlaybackMediaLibraryCallback @Inject constructor(
    private val automotiveMediaCatalog: AutomotiveMediaCatalog,
) : MediaLibraryService.MediaLibrarySession.Callback {

    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<List<MediaItem>> =
        Futures.immediateFuture(mediaItems.map(::toPlayableMediaItem))

    override fun onSetMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val playableItems = mediaItems.map(::toPlayableMediaItem)
        return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(
                playableItems,
                startIndex,
                startPositionMs,
            ),
        )
    }

    private fun toPlayableMediaItem(mediaItem: MediaItem): MediaItem {
        val uri = mediaItem.localConfiguration?.uri ?: mediaItem.requestMetadata.mediaUri
            ?: return mediaItem
        return mediaItem.buildUpon().setUri(uri).build()
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val root = automotiveMediaCatalog.rootMediaItem()
        return Futures.immediateFuture(LibraryResult.ofItem(root, params))
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val children = runBlocking { automotiveMediaCatalog.children(parentId) }
        return Futures.immediateFuture(
            LibraryResult.ofItemList(ImmutableList.copyOf(children), params),
        )
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val item = runBlocking {
            automotiveMediaCatalog.children(AutomotiveMediaCatalog.ROOT_ID)
                .find { it.mediaId == mediaId }
                ?: automotiveMediaCatalog.children(AutomotiveMediaCatalog.NOW_PLAYING_ID)
                    .find { it.mediaId == mediaId }
                ?: automotiveMediaCatalog.children(AutomotiveMediaCatalog.FAVORITES_ID)
                    .find { it.mediaId == mediaId }
                ?: automotiveMediaCatalog.children(AutomotiveMediaCatalog.RECENT_ID)
                    .find { it.mediaId == mediaId }
        }
        return if (item != null) {
            Futures.immediateFuture(LibraryResult.ofItem(item, null))
        } else {
            Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED))
        }
    }
}
