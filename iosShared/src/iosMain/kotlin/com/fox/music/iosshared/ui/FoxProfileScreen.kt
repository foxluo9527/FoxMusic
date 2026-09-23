package com.fox.music.iosshared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.fox.music.core.data.mapper.toAlbum
import com.fox.music.core.data.mapper.toArtist
import com.fox.music.core.data.mapper.toListeningStats
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.data.mapper.toPlayHistory
import com.fox.music.core.data.mapper.toPlaylist
import com.fox.music.core.data.mapper.toUser
import com.fox.music.core.model.music.Music
import com.fox.music.feature.profile.ui.screen.ProfileContent
import com.fox.music.feature.profile.viewmodel.ProfileIntent
import com.fox.music.feature.profile.viewmodel.ProfileState
import com.fox.music.iosshared.FoxAuthStore
import com.fox.music.iosshared.FoxComposeState
import com.fox.music.iosshared.FoxIosBridge
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.PlayableSong
import com.fox.music.iosshared.foxComposeViewController
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

private data class ProfileSongCollection(
    val title: String,
    val songs: List<Music>,
)

@OptIn(ExperimentalMaterial3Api::class)
object FoxProfileScreen {

    private val api = FoxMusicApi()

    fun makeProfileViewController(
        onLogin: () -> Unit,
        /**
         * 右上角齿轮：无论是否登录都进设置页。
         * 之前是 `if (loggedIn) onLogout() else onLogin()` —— 游客点齿轮弹登录、
         * 已登录点齿轮直接退出登录，两个都不对（ProfileContent 的登录态分支还把齿轮
         * 接到了 onLogin）。设置页里的「退出登录」才是退出入口（与 Android 一致）。
         */
        onSettings: () -> Unit,
        onPlaylistClick: (Long, String) -> Unit,
        onArtistClick: (Long) -> Unit,
        onAlbumClick: (Long) -> Unit,
        onMusicClick: (PlayableSong?) -> Unit,
    ): UIViewController = foxComposeViewController {
        val authRevision = FoxAuthStore.revision
        val loggedIn = FoxAuthStore.isLoggedIn
        val scope = rememberCoroutineScope()
        var state by remember { mutableStateOf(ProfileState()) }
        var collection by remember { mutableStateOf<ProfileSongCollection?>(null) }

        LaunchedEffect(authRevision) {
            collection = null
            if (!FoxAuthStore.isLoggedIn) {
                state = ProfileState()
                return@LaunchedEffect
            }
            state = state.copy(isLoading = true, error = null)
            try {
                val user = api.fetchProfile()?.toUser()
                val playlists = try {
                    api.fetchUserPlaylists().map { it.toPlaylist() }
                } catch (_: Exception) {
                    emptyList()
                }
                val historyPage = try {
                    api.fetchPlayHistoryPage(1, 1)
                } catch (_: Exception) {
                    null
                }
                val stats = try {
                    api.fetchListeningStats(7)?.toListeningStats()
                } catch (_: Exception) {
                    null
                }
                val favMusic = try {
                    api.fetchFavoriteMusics(1, 50)
                } catch (_: Exception) {
                    null
                }
                val favPlaylists = try {
                    api.fetchFavoritePlaylists(1, 50)
                } catch (_: Exception) {
                    null
                }
                val favAlbums = try {
                    api.fetchFavoriteAlbums(1, 50)
                } catch (_: Exception) {
                    null
                }
                val favArtists = try {
                    api.fetchFavoriteArtists(1, 50)
                } catch (_: Exception) {
                    null
                }
                state = ProfileState(
                    user = user,
                    playlists = playlists,
                    favoriteMusicTotal = favMusic?.total ?: 0,
                    favoritePlaylists = favPlaylists?.list.orEmpty().map { it.toPlaylist() },
                    favoritePlaylistTotal = favPlaylists?.total ?: 0,
                    favoriteAlbums = favAlbums?.list.orEmpty().map { it.toAlbum() },
                    favoriteAlbumTotal = favAlbums?.total ?: 0,
                    favoriteArtists = favArtists?.list.orEmpty().map { it.toArtist() },
                    favoriteArtistTotal = favArtists?.total ?: 0,
                    playHistoryTotal = historyPage?.total ?: 0,
                    listeningStats = stats,
                    isLoading = false,
                    error = if (user == null) "无法加载资料，请重新登录" else null,
                )
            } catch (e: Exception) {
                state = ProfileState(isLoading = false, error = e.message ?: "加载失败")
            }
        }

        fun openCollection(title: String, load: suspend () -> List<Music>) {
            scope.launch {
                val songs = try {
                    load()
                } catch (_: Exception) {
                    emptyList()
                }
                collection = ProfileSongCollection(title, songs)
            }
        }

        val opened = collection
        if (opened != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(opened.title) },
                    navigationIcon = {
                        IconButton(onClick = { collection = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
                FoxSongsScreen(
                    songs = opened.songs,
                    playingId = FoxComposeState.currentSongId,
                    isPlaying = FoxComposeState.isPlaying,
                    onPlaySong = { onMusicClick(it) },
                    playableOf = { FoxIosBridge.playableSong(it) },
                    title = "",
                )
            }
        } else {
            ProfileContent(
                state = state,
                onIntent = { intent ->
                    when (intent) {
                        is ProfileIntent.OnPlaylistClick ->
                            onPlaylistClick(intent.playlist.id, intent.playlist.title)
                        ProfileIntent.OnFavoriteTracksClick -> openCollection("我的收藏") {
                            api.fetchFavoriteMusics(1, 50).list.map { it.toMusic() }
                        }
                        else -> Unit
                    }
                },
                isLogin = loggedIn,
                onLogin = onLogin,
                onFavoritePlaylistClick = { onPlaylistClick(it.id, it.title) },
                onAlbumClick = onAlbumClick,
                onPlayHistoryClick = {
                    openCollection("历史播放") {
                        api.fetchPlayHistory(1, 50).map { it.toPlayHistory().music }
                    }
                },
                onArtistClick = onArtistClick,
                onSettingsClick = onSettings,
                onCreatePlaylistFromVm = {},
            )
        }
    }
}
