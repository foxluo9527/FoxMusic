package com.fox.music.core.player.di

import com.fox.music.core.player.MusicController
import com.fox.music.core.player.MusicControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    @Singleton
    abstract fun bindMusicController(impl: MusicControllerImpl): MusicController
}
