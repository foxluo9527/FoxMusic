package com.fox.music.feature.automotive

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
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

class CarModeSearchScreen(carContext: CarContext) :
    Screen(carContext),
    SearchTemplate.SearchCallback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        CarEntryPoint::class.java,
    )
    private val musicController = entryPoint.musicController()
    private val searchMusicUseCase = entryPoint.searchMusicUseCase()
    private val getHotKeywordsUseCase = entryPoint.getHotKeywordsUseCase()

    private var searchQuery: String = ""
    private var results: List<Music> = emptyList()
    private var hotKeywords: List<String> = emptyList()
    private var isSearching = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
        scope.launch {
            hotKeywords = when (val result = getHotKeywordsUseCase(limit = 6)) {
                is Result.Success -> result.data.map { it.keyword }
                else -> emptyList()
            }
            invalidate()
        }
    }

    override fun onSearchTextChanged(searchText: String) {
        searchQuery = searchText
    }

    override fun onSearchSubmitted(searchText: String) {
        searchQuery = searchText
        submitSearch(searchText)
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        if (searchQuery.isBlank()) {
            hotKeywords.forEach { keyword ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(keyword)
                        .setOnClickListener {
                            searchQuery = keyword
                            submitSearch(keyword)
                        }
                        .build(),
                )
            }
        } else if (isSearching) {
            listBuilder.addItem(Row.Builder().setTitle("搜索中...").build())
        } else if (results.isEmpty()) {
            listBuilder.addItem(Row.Builder().setTitle("无搜索结果").build())
        } else {
            results.forEach { music ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(music.title)
                        .addText(music.artists.joinToString { it.name })
                        .setOnClickListener {
                            val index = results.indexOfFirst { it.id == music.id }.coerceAtLeast(0)
                            musicController.setPlaylist(results, startIndex = index, key = "car_mode_search")
                            musicController.play()
                        }
                        .build(),
                )
            }
        }
        return SearchTemplate.Builder(this)
            .setSearchHint("搜索歌曲")
            .setHeaderAction(Action.BACK)
            .setShowKeyboardByDefault(searchQuery.isBlank())
            .setItemList(listBuilder.build())
            .setInitialSearchText(searchQuery)
            .build()
    }

    private fun submitSearch(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            results = emptyList()
            invalidate()
            return
        }
        scope.launch {
            isSearching = true
            invalidate()
            results = when (val result = searchMusicUseCase(keyword = trimmed, page = 1, limit = 20)) {
                is Result.Success -> result.data.list
                else -> emptyList()
            }
            isSearching = false
            invalidate()
        }
    }
}
