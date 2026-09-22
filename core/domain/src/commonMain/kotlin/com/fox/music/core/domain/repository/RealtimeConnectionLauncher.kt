package com.fox.music.core.domain.repository

/** 启动/停止实时连接前台保活服务（由 app 模块实现） */
interface RealtimeConnectionLauncher {
    fun startConnectionService()
    fun stopConnectionService()
}
