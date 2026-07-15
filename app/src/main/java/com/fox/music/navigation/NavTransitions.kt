package com.fox.music.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

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
