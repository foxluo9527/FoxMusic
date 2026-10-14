package com.fox.music.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import com.fox.music.feature.assistant.ASSISTANT_ROUTE
import com.fox.music.feature.assistant.ui.screen.AssistantScreen

/**
 * AI 音乐助手导航图（功能二·端侧）。
 *
 * 入口在首页品牌区搜索栏内的「AI 助手」pill（HomeBrandHero），未登录门控在
 * TabNavGraph 的调用点统一处理（与消息 Tab 各入口的惯例一致）。
 */
fun NavGraphBuilder.assistantNavGraph(context: NavGraphContext) {
    composableStack(ASSISTANT_ROUTE) {
        AssistantScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { context.navController.popBackStack() },
        )
    }
}
