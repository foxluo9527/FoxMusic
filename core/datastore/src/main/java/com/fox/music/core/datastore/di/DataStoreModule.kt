package com.fox.music.core.datastore.di

import com.fox.music.core.common.player.PlaybackStateStore
import com.fox.music.core.datastore.FoxPreferencesDataStore
import com.fox.music.core.datastore.PlaybackStateStoreImpl
import com.fox.music.core.datastore.TokenManagerImpl
import com.fox.music.core.network.token.TokenManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {

    @Binds
    @Singleton
    abstract fun bindTokenManager(impl: TokenManagerImpl): TokenManager

    @Binds
    @Singleton
    abstract fun bindPlaybackStateStore(impl: PlaybackStateStoreImpl): PlaybackStateStore
}
