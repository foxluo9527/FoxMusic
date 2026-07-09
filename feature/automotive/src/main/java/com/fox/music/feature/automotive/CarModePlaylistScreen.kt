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
import dagger.hilt.android.EntryPointAccessors
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CarModePlaylistScreen(
    carContext: CarContext,
    private val playlistId: Long,
    private val playlistTitle: String,
) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        CarEntryPoint::class.java,
    )
    private val musicController = entryPoint.musicController()
    private val getPlaylistDetailUseCase = entryPoint.getPlaylistDetailUseCase()

    private var tracks: List<Music> = emptyList()
    private var isLoading = true

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
        scope.launch {
            when (val result = getPlaylistDetailUseCase(playlistId, page = 1, limit = 50)) {
                is Result.Success -> tracks = result.data.tracks.list
                else -> tracks = emptyList()
            }
            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ItemList.Builder()
        if (isLoading) {
            builder.addItem(Row.Builder().setTitle("加载中...").build())
        } else if (tracks.isEmpty()) {
            builder.addItem(Row.Builder().setTitle("暂无歌曲").build())
        } else {
            tracks.forEach { music ->
                builder.addItem(
                    Row.Builder()
                        .setTitle(music.title)
                        .addText(music.artists.joinToString { it.name })
                        .setOnClickListener {
                            val index = tracks.indexOfFirst { it.id == music.id }.coerceAtLeast(0)
                            musicController.setPlaylist(tracks, startIndex = index, key = "car_mode_playlist")
                            musicController.play()
                        }
                        .build(),
                )
            }
        }
        return ListTemplate.Builder()
            .setSingleList(builder.build())
            .setTitle(playlistTitle)
            .setHeaderAction(Action.BACK)
            .build()
    }
}
