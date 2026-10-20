package com.fox.music.core.data.di

import com.fox.music.core.common.network.NetworkMonitor
import com.fox.music.core.common.session.TokenManager
import com.fox.music.core.data.realtime.ActiveChatTracker
import com.fox.music.core.data.realtime.ChatRepositoryMessageGateway
import com.fox.music.core.data.realtime.RealtimeMessageCoordinator
import com.fox.music.core.data.realtime.RealtimeMessageGateway
import com.fox.music.core.data.realtime.RealtimeNotificationRouting
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.domain.repository.RealtimeConnectionLauncher
import com.fox.music.core.domain.repository.RealtimeNotificationDispatcher
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.network.websocket.WebSocketManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 实时链路的 Android 装配。
 *
 * 为什么单独一个 Module：这几个类（[ActiveChatTracker] / [RealtimeMessageCoordinator]）
 * 原先靠 `@Inject constructor` + `@Singleton` 由 Hilt 自动装配，但跨端后它们移到了
 * KMP 的 :core:data，而 KMP 模块不能跑 KSP/Hilt（见 :core:data/build.gradle.kts 注释），
 * 注解必须去掉，因此改在这里手工 new 一次。
 *
 * iOS 端不走这里：iosShared 里直接构造，语义一致。
 */
@Module
@InstallIn(SingletonComponent::class)
object RealtimeModule {

    @Provides
    @Singleton
    fun provideActiveChatTracker(): ActiveChatTracker = ActiveChatTracker()

    /** 实时落地出口：Android 侧接到 ChatRepository（Room） */
    @Provides
    @Singleton
    fun provideRealtimeMessageGateway(
        chatRepository: ChatRepository,
    ): RealtimeMessageGateway = ChatRepositoryMessageGateway(chatRepository)

    @Provides
    @Singleton
    fun provideRealtimeMessageCoordinator(
        webSocketManager: WebSocketManager,
        tokenManager: TokenManager,
        messageGateway: RealtimeMessageGateway,
        userPreferencesRepository: UserPreferencesRepository,
        notificationDispatcher: RealtimeNotificationDispatcher,
        activeChatTracker: ActiveChatTracker,
        notificationRouting: RealtimeNotificationRouting,
        connectionLauncher: RealtimeConnectionLauncher,
        networkMonitor: NetworkMonitor,
    ): RealtimeMessageCoordinator = RealtimeMessageCoordinator(
        webSocketManager = webSocketManager,
        tokenManager = tokenManager,
        messageGateway = messageGateway,
        userPreferencesRepository = userPreferencesRepository,
        notificationDispatcher = notificationDispatcher,
        activeChatTracker = activeChatTracker,
        notificationRouting = notificationRouting,
        connectionLauncher = connectionLauncher,
        // Android 走 ConnectivityManager 实现，网络恢复时自动重连
        networkMonitor = networkMonitor,
    )
}
