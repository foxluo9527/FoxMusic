package com.fox.music.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

object FoxTransitions {
    const val TAB_DURATION_MS = 220
    const val STACK_DURATION_MS = 320
    const val PLAYER_DURATION_MS = 720
}

fun AnimatedContentTransitionScope<*>.tabEnter(): EnterTransition =
    fadeIn(tween(FoxTransitions.TAB_DURATION_MS))

fun AnimatedContentTransitionScope<*>.tabExit(): ExitTransition =
    fadeOut(tween(FoxTransitions.TAB_DURATION_MS))

fun AnimatedContentTransitionScope<*>.stackEnter(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(FoxTransitions.STACK_DURATION_MS),
    )

fun AnimatedContentTransitionScope<*>.stackExit(): ExitTransition =
    ExitTransition.None

fun AnimatedContentTransitionScope<*>.stackPopEnter(): EnterTransition =
    EnterTransition.None

fun AnimatedContentTransitionScope<*>.stackPopExit(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(FoxTransitions.STACK_DURATION_MS),
    )

fun AnimatedContentTransitionScope<*>.playerEnter(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Up,
        animationSpec = tween(FoxTransitions.PLAYER_DURATION_MS),
    )

fun AnimatedContentTransitionScope<*>.playerExit(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Down,
        animationSpec = tween(FoxTransitions.PLAYER_DURATION_MS),
    )

/**
 * 从底部升起的二级页（导入详情这类「盖上来的一层」）：
 * 进入自下而上滑入，返回时自上而下滑出消失——与弹层收到下方的手感一致。
 *
 * 注意：系统的「预测性返回」预览会把整个界面缩小成圆角卡片，看起来像缩放动画。
 * 该预览在 `enableOnBackInvokedCallback=true` 时由系统播放，Compose 拦截不了，
 * 所以清单里已把该开关关掉，返回动画统一由这里的转场负责。
 */
fun AnimatedContentTransitionScope<*>.sheetEnter(): EnterTransition =
    slideInVertically(tween(FoxTransitions.STACK_DURATION_MS)) { it } +
        fadeIn(tween(FoxTransitions.STACK_DURATION_MS))

fun AnimatedContentTransitionScope<*>.sheetExit(): ExitTransition = ExitTransition.None

fun AnimatedContentTransitionScope<*>.sheetPopEnter(): EnterTransition = EnterTransition.None

fun AnimatedContentTransitionScope<*>.sheetPopExit(): ExitTransition =
    slideOutVertically(tween(FoxTransitions.STACK_DURATION_MS)) { it } +
        fadeOut(tween(FoxTransitions.STACK_DURATION_MS))

/** 播放页上叠的详情路由：进入时不播放页退场，返回时不播放页入场 */
fun isOverlayDetailRoute(route: String?): Boolean {
    if (route.isNullOrBlank()) return false
    return route.startsWith("post_detail") ||
        route.startsWith("playlist_detail") ||
        route.startsWith("artist_detail") ||
        route.startsWith("music_comment_detail")
}
