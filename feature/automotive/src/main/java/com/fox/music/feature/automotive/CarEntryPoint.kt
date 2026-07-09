package com.fox.music.feature.automotive

import com.fox.music.core.domain.usecase.GetHotKeywordsUseCase
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.domain.usecase.GetPlaylistDetailUseCase
import com.fox.music.core.domain.usecase.GetRecommendedPlaylistsUseCase
import com.fox.music.core.domain.usecase.SearchMusicUseCase
import com.fox.music.core.player.controller.MusicController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CarEntryPoint {
    fun musicController(): MusicController
    fun getRecommendedPlaylistsUseCase(): GetRecommendedPlaylistsUseCase
    fun getMusicListUseCase(): GetMusicListUseCase
    fun getPlaylistDetailUseCase(): GetPlaylistDetailUseCase
    fun searchMusicUseCase(): SearchMusicUseCase
    fun getHotKeywordsUseCase(): GetHotKeywordsUseCase
}
