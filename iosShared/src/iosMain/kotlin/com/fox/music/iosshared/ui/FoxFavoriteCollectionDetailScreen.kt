package com.fox.music.iosshared.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fox.music.core.data.mapper.toAlbum
import com.fox.music.core.data.mapper.toArtist
import com.fox.music.core.data.mapper.toPlaylist
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Playlist
import com.fox.music.feature.profile.ui.screen.FavoriteCollectionDetailContent
import com.fox.music.feature.profile.ui.screen.FavoriteCollectionType
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.foxComposeViewController
import platform.UIKit.UIViewController

/**
 * 收藏详情页（iOS）：通用「返回 + 标题」顶部栏 + 复用「我的」页的收藏列表项样式。
 * type 取值与共享 [FavoriteCollectionType] 对齐：playlists / albums / artists。
 * 列表分页拉全（上限 1000 条防失控）。
 */
object FoxFavoriteCollectionDetailScreen {

    private val api = FoxMusicApi()
    private const val PAGE_SIZE = 50
    private const val MAX_PAGES = 20

    fun makeViewController(
        type: String,
        title: String,
        onBack: () -> Unit,
        onPlaylistClick: (Long, String) -> Unit = { _, _ -> },
        onAlbumClick: (Long) -> Unit = {},
        onArtistClick: (Long) -> Unit = {},
    ): UIViewController = foxComposeViewController {
        val collectionType = when (type.lowercase()) {
            "albums" -> FavoriteCollectionType.ALBUMS
            "artists" -> FavoriteCollectionType.ARTISTS
            else -> FavoriteCollectionType.PLAYLISTS
        }
        var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
        var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
        var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(collectionType) {
            isLoading = true
            error = null
            try {
                when (collectionType) {
                    FavoriteCollectionType.PLAYLISTS -> {
                        val acc = mutableListOf<Playlist>()
                        var page = 1
                        while (page <= MAX_PAGES) {
                            val resp = api.fetchFavoritePlaylists(page, PAGE_SIZE)
                            acc += resp.list.map { it.toPlaylist() }
                            if (resp.list.isEmpty() || acc.size >= resp.total) break
                            page++
                        }
                        playlists = acc
                    }

                    FavoriteCollectionType.ALBUMS -> {
                        val acc = mutableListOf<Album>()
                        var page = 1
                        while (page <= MAX_PAGES) {
                            val resp = api.fetchFavoriteAlbums(page, PAGE_SIZE)
                            acc += resp.list.map { it.toAlbum() }
                            if (resp.list.isEmpty() || acc.size >= resp.total) break
                            page++
                        }
                        albums = acc
                    }

                    FavoriteCollectionType.ARTISTS -> {
                        val acc = mutableListOf<Artist>()
                        var page = 1
                        while (page <= MAX_PAGES) {
                            val resp = api.fetchFavoriteArtists(page, PAGE_SIZE)
                            acc += resp.list.map { it.toArtist() }
                            if (resp.list.isEmpty() || acc.size >= resp.total) break
                            page++
                        }
                        artists = acc
                    }
                }
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "加载失败"
            }
            isLoading = false
        }

        FavoriteCollectionDetailContent(
            title = title,
            type = collectionType,
            onBack = onBack,
            playlists = playlists,
            albums = albums,
            artists = artists,
            isLoading = isLoading,
            error = error,
            onPlaylistClick = { onPlaylistClick(it.id, it.title) },
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
        )
    }
}
