package com.fox.music.feature.assistant

/** AI 助手对话面板路由（无参数：会话标识由 ViewModel 状态持有，进面板即新会话）。 */
const val ASSISTANT_ROUTE = "assistant"

fun assistantRoute(): String = ASSISTANT_ROUTE
