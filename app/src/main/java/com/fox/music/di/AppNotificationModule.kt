package com.fox.music.di

import com.fox.music.core.data.realtime.RealtimeNotificationRouting
import com.fox.music.core.domain.repository.RealtimeConnectionLauncher
import com.fox.music.core.domain.repository.RealtimeNotificationDispatcher
import com.fox.music.notification.FoxNotificationManager
import com.fox.music.notification.NotificationRouting
import com.fox.music.realtime.RealtimeConnectionLauncherImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppNotificationModule {

    @Binds
    @Singleton
    abstract fun bindRealtimeNotificationDispatcher(
        impl: FoxNotificationManager,
    ): RealtimeNotificationDispatcher

    @Binds
    @Singleton
    abstract fun bindRealtimeNotificationRouting(
        impl: NotificationRouting,
    ): RealtimeNotificationRouting

    @Binds
    @Singleton
    abstract fun bindRealtimeConnectionLauncher(
        impl: RealtimeConnectionLauncherImpl,
    ): RealtimeConnectionLauncher
}
