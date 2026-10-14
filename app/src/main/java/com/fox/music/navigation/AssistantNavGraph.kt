package com.fox.music.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.fox.music.core.model.music.DetailType
import com.fox.music.feature.assistant.ASSISTANT_ROUTE
import com.fox.music.feature.assistant.ui.screen.AssistantScreen
import com.fox.music.feature.chat.MESSAGES_ROUTE
import com.fox.music.feature.discover.DISCOVER_ROUTE
import com.fox.music.feature.discover.artistDetailRoute
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.playlist.ui.component.playlistDetailRoute
import com.fox.music.feature.profile.ui.screen.PROFILE_ROUTE
import com.fox.music.feature.search.searchRoute
import com.fox.music.feature.social.COMMUNITY_PLAZA_ROUTE
import com.blankj.utilcode.util.ToastUtils

/**
 * AI 音乐助手导航图（功能二·端侧）。
 *
 * 入口在首页品牌区搜索栏内的「AI 助手」pill（HomeBrandHero），未登录门控在
 * TabNavGraph 的调用点统一处理（与消息 Tab 各入口的惯例一致）。
 *
 * 面板内的导航类动作（open_screen / search_in_app）经 AssistantEffect 上浮到这里，
 * 由本文件把服务端 screen 白名单集中映射到真实路由——core 层无 NavController，
 * 这是架构约束（方案 4.1：端侧才有导航能力）。
 */
fun NavGraphBuilder.assistantNavGraph(context: NavGraphContext) {
    composableStack(ASSISTANT_ROUTE) {
        AssistantScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { context.navController.popBackStack() },
            onOpenScreen = { screen, targetId ->
                openAssistantScreen(context.navController, screen, targetId)
            },
            onSearchInApp = { query ->
                context.navController.navigate(searchRoute(query = query))
            },
        )
    }
}

/**
 * 把 open_screen 的 screen 标识映射到真实路由并跳转。
 *
 * - Tab 路由（home/discover/community/messages/profile）复用底栏同款切栈选项，
 *   避免在助手之上堆叠重复 Tab；
 * - 详情 / 搜索等栈内路由直接 push，返回可回到助手面板；
 * - 无对应路由或缺少必要参数（如歌单缺 target_id）时 Toast 提示，绝不抛异常崩栈。
 */
private fun openAssistantScreen(
    navController: NavHostController,
    screen: String,
    targetId: Long?,
) {
    // Tab 路由：与 AppBottomNavigationBar 的切栈逻辑保持一致
    val tabRoute = when (screen) {
        "home" -> HOME_ROUTE
        "discover" -> DISCOVER_ROUTE
        "community" -> COMMUNITY_PLAZA_ROUTE
        "messages" -> MESSAGES_ROUTE
        "profile" -> PROFILE_ROUTE
        else -> null
    }
    if (tabRoute != null) {
        navController.navigate(tabRoute) {
            popUpTo(HOME_ROUTE) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        return
    }

    // 栈内路由：拼出目标 route 再统一 push；缺参/无路由时 route 为 null
    val route: String? = when (screen) {
        "search" -> searchRoute()
        "playlist" -> targetId?.let { playlistDetailRoute(it, DetailType.PLAYLIST) }
        "album" -> targetId?.let { playlistDetailRoute(it, DetailType.ALBUM) }
        "artist" -> targetId?.let { artistDetailRoute(it) }
        "rank" -> targetId?.let { playlistDetailRoute(it, DetailType.RANK) }
        "favorites" -> playlistDetailRoute(DetailType.FAVORITE_MUSIC_ID, DetailType.FAVORITE_MUSIC)
        "history" -> playlistDetailRoute(DetailType.PLAY_HISTORY_ID, DetailType.PLAY_HISTORY)
        // "music" 无独立详情路由（点歌即播，走播放器）；其余未知值一并兜底
        else -> null
    }
    if (route != null) {
        navController.navigate(route)
    } else {
        ToastUtils.showShort("该页面暂未接入")
    }
}
