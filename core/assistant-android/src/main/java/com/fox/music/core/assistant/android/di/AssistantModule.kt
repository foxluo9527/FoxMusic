package com.fox.music.core.assistant.android.di

import com.fox.music.core.assistant.AssistantActionDispatcher
import com.fox.music.core.assistant.AssistantRepository
import com.fox.music.core.assistant.android.AssistantActionDispatcherImpl
import com.fox.music.core.assistant.android.AssistantRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** AI 助手 core 层 Hilt 装配（供多 feature 复用，不含 @HiltViewModel） */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantModule {

    @Binds
    @Singleton
    abstract fun bindAssistantRepository(impl: AssistantRepositoryImpl): AssistantRepository

    @Binds
    @Singleton
    abstract fun bindAssistantActionDispatcher(impl: AssistantActionDispatcherImpl): AssistantActionDispatcher
}
