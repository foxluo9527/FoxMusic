package com.fox.music.feature.player.ui.screen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 桌面歌词悬浮窗的「设置」不再在悬浮窗里展开面板，而是把 App 拉回
 * 播放页 → 歌词页 → 展开歌词设置。
 *
 * 冷启动和热启动都要生效，所以用进程内一次性信号：
 * - Activity 收到 `open_lyric_settings` 时 [request]
 * - MainScreen 看到 pending 时导航到 [PLAYER_ROUTE]
 * - PlayerScreen 挂载后消费信号，切到歌词页并打开设置面板
 */
object DesktopLyricSettingsRequest {
    const val EXTRA_OPEN_LYRIC_SETTINGS = "open_lyric_settings"

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }
}
