package com.fox.music.core.player.automotive

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.domain.repository.FavoriteRepository
import com.fox.music.core.domain.usecase.GetPlayHistoryUseCase
import com.fox.music.core.model.music.Music
import com.fox.music.core.player.controller.MusicController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomotiveMediaCatalog @Inject constructor(
    private val musicController: MusicController,
    private val favoriteRepository: FavoriteRepository,
    private val getPlayHistoryUseCase: GetPlayHistoryUseCase,
) {
    suspend fun rootItems(): List<MediaItem> = listOf(
        folderItem(NOW_PLAYING_ID, "正在播放"),
        folderItem(FAVORITES_ID, "我的收藏"),
        folderItem(RECENT_ID, "最近播放"),
    )

    suspend fun children(parentId: String): List<MediaItem> = when (parentId) {
        ROOT_ID -> rootItems()
        NOW_PLAYING_ID -> musicController.playerState.value.playlist.map { it.toPlayableMediaItem() }
        FAVORITES_ID -> favoriteRepository.getFavoriteMusics(page = 1, limit = 50)
            .getOrNull()?.list?.map { it.toPlayableMediaItem() }.orEmpty()
        RECENT_ID -> getPlayHistoryUseCase(page = 1, limit = 50)
            .getOrNull()?.list?.map { it.music.toPlayableMediaItem() }.orEmpty()
        else -> emptyList()
    }

    fun rootMediaItem(): MediaItem = folderItem(ROOT_ID, "FoxMusic")

    private fun folderItem(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build(),
            )
            .build()

    companion object {
        const val ROOT_ID = "foxmusic_root"
        const val NOW_PLAYING_ID = "foxmusic_now_playing"
        const val FAVORITES_ID = "foxmusic_favorites"
        const val RECENT_ID = "foxmusic_recent"
    }
}

fun Music.toPlayableMediaItem(): MediaItem {
    val builder = MediaItem.Builder()
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artists.joinToString(", ") { it.name })
                .setAlbumTitle(album?.title)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .apply {
                    coverImage?.let { setArtworkUri(MediaUrlResolver.resolve(it)?.toUri()) }
                }
                .build(),
        )
    val resolvedUrl = MediaUrlResolver.resolve(url)
    if (!resolvedUrl.isNullOrBlank()) {
        val uri = resolvedUrl.toUri()
        builder
            .setUri(uri)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(uri)
                    .build(),
            )
    }
    return builder.build()
}
