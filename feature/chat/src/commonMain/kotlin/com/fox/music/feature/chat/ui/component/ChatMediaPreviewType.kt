package com.fox.music.feature.chat.ui.component

/**
 * 聊天媒体预览类型。从 ChatMediaPreviewDialog 抽离以便 commonMain 侧
 *（ChatMessageMediaHelper 等纯 Kotlin 工具）引用；原枚举定义已删除。
 */
enum class ChatMediaPreviewType {
    IMAGE,
    VIDEO,
    LIVE,
}
