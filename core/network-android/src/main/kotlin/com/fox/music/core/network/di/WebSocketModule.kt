package com.fox.music.core.network.di

import com.fox.music.core.network.websocket.WebSocketManager
import com.fox.music.core.network.websocket.WebSocketManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WebSocketModule {

    @Binds
    @Singleton
    abstract fun bindWebSocketManager(
        impl: WebSocketManagerImpl
    ): WebSocketManager
}
