package com.fox.music.core.ui.view

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.fox.music.core.common.session.TokenManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TokenManagerEntryPoint {
    fun getTokenManager(): TokenManager
}