package com.fox.music.feature.player.lyric.manager

import com.fox.music.core.domain.repository.UserPreferencesRepository
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

    /** 桌面歌词需要读取当前主题强调色，才能与「跟随主题」配色保持一致。 */
    fun getUserPreferencesRepository(): UserPreferencesRepository
}