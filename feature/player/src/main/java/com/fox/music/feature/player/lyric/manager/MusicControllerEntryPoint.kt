package com.fox.music.feature.player.lyric.manager

import com.fox.music.core.player.controller.MusicController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// 在任意位置定义 EntryPoint
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MusicControllerEntryPoint {
    // 注意：这里返回的是 MusicController 接口，不是 MusicControllerImpl
    fun getMusicController(): MusicController
}