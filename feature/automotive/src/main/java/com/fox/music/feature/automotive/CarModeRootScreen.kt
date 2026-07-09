package com.fox.music.feature.automotive

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarText
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.fox.music.core.common.result.Result
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import dagger.hilt.android.EntryPointAccessors
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CarModeRootScreen(carContext: CarContext) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        CarEntryPoint::class.java,
    )
    private val musicController = entryPoint.musicController()
    private val getRecommendedPlaylistsUseCase = entryPoint.getRecommendedPlaylistsUseCase()
    private val getMusicListUseCase = entryPoint.getMusicListUseCase()

    private var playlists: List<Playlist> = emptyList()
    private var songs: List<Music> = emptyList()
    private var isLoading = true
    private var errorMessage: String? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
        scope.launch { loadContent() }
    }

    private suspend fun loadContent() {
        isLoading = true
        invalidate()
        playlists = when (val result = getRecommendedPlaylistsUseCase(page = 1, limit = 10)) {
            is Result.Success -> result.data.list
            is Result.Error -> {
                errorMessage = result.message
                emptyList()
            }
            else -> emptyList()
        }
        songs = when (val result = getMusicListUseCase(page = 1, limit = 20, sort = "recommend")) {
            is Result.Success -> result.data.list
            else -> emptyList()
        }
        isLoading = false
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val builder = ItemList.Builder()
        builder.addItem(
            Row.Builder()
                .setTitle("搜索音乐")
                .setOnClickListener { screenManager.push(CarModeSearchScreen(carContext)) }
                .build(),
        )
        if (isLoading) {
            builder.addItem(Row.Builder().setTitle("加载中...").build())
        } else if (errorMessage != null && playlists.isEmpty() && songs.isEmpty()) {
            builder.addItem(Row.Builder().setTitle(errorMessage!!).build())
        } else {
            if (playlists.isNotEmpty()) {
                builder.addItem(Row.Builder().setTitle(CarText.create("推荐歌单")).build())
                playlists.forEach { playlist ->
                    builder.addItem(
                        Row.Builder()
                            .setTitle(playlist.title)
                            .addText("${playlist.trackCount} 首")
                            .setOnClickListener {
                                screenManager.push(
                                    CarModePlaylistScreen(
                                        carContext,
                                        playlistId = playlist.id,
                                        playlistTitle = playlist.title,
                                    ),
                                )
                            }
                            .build(),
                    )
                }
            }
            if (songs.isNotEmpty()) {
                builder.addItem(Row.Builder().setTitle(CarText.create("推荐歌曲")).build())
                songs.forEach { music ->
                    builder.addItem(songRow(music, songs))
                }
            }
        }
        return ListTemplate.Builder()
            .setSingleList(builder.build())
            .setTitle("FoxMusic")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun songRow(music: Music, playlist: List<Music>): Row =
        Row.Builder()
            .setTitle(music.title)
            .addText(music.artists.joinToString { it.name })
            .setOnClickListener {
                val index = playlist.indexOfFirst { it.id == music.id }.coerceAtLeast(0)
                musicController.setPlaylist(playlist, startIndex = index, key = "car_mode")
                musicController.play()
            }
            .build()
}
