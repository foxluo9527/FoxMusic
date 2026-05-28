package com.fox.music.core.player.di

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.fox.music.core.domain.repository.PlaybackCacheRepository
import com.fox.music.core.player.cache.PlaybackCacheManager
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.player.controller.MusicControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @OptIn(UnstableApi::class)
    @Binds
    @Singleton
    abstract fun bindMusicController(impl: MusicControllerImpl): MusicController

    @Binds
    @Singleton
    abstract fun bindPlaybackCacheRepository(impl: PlaybackCacheManager): PlaybackCacheRepository
}
