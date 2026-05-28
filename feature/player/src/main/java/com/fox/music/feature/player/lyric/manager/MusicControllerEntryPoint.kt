package com.fox.music.feature.player.lyric.manager

import com.fox.music.core.domain.usecase.ToggleMusicFavoriteUseCase
import com.fox.music.core.player.controller.MusicController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MusicControllerEntryPoint {
    fun getMusicController(): MusicController

    fun getToggleMusicFavoriteUseCase(): ToggleMusicFavoriteUseCase
}