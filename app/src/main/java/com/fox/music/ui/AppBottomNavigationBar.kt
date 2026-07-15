package com.fox.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.fox.music.core.ui.theme.FloatingNavShape
import com.fox.music.core.ui.theme.FoxSemanticColors
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.feature.discover.DISCOVER_ROUTE
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.profile.ui.screen.PROFILE_ROUTE
import kotlin.math.roundToInt

/** 底部 Tab 路由：仅这些页面内嵌导航栏，以参与 NavHost 转场与可预测返回动画。 */
val TAB_ROOT_ROUTES: Set<String> = AppDestinations.entries.map { it.route }.toSet()

private val TabIndicatorSize = 44.dp

/**
 * 各 Tab 目的地各自挂载底栏，切换时 composable 会重建。
 * 用进程内状态承接指示器位置，才能在新实例上从旧 index 动画到新 index。
 */
private object TabIndicatorMotion {
    var index: Float = 0f
}

enum class AppDestinations(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(HOME_ROUTE, "首页", Icons.Filled.Home, Icons.Outlined.Home),
    DISCOVER(DISCOVER_ROUTE, "发现", Icons.Filled.Explore, Icons.Outlined.Explore),
    PROFILE(PROFILE_ROUTE, "我的", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
}

@Composable
fun AppBottomNavigationBar(
    navController: NavHostController,
    selectedRoute: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val selectedIndex = AppDestinations.entries
        .indexOfFirst { it.route == selectedRoute }
        .coerceAtLeast(0)
    val tabCount = AppDestinations.entries.size

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = FoxSpacing.navInset, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(FloatingNavShape)
                .background(scheme.surface)
                .border(
                    width = 1.dp,
                    color = FoxSemanticColors.glassBorder.copy(
                        alpha = if (scheme.background.luminanceApprox() < 0.5f) 0.18f else 0.1f,
                    ),
                    shape = FloatingNavShape,
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            var contentWidthPx by remember { mutableFloatStateOf(0f) }
            var contentHeightPx by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .onSizeChanged {
                        contentWidthPx = it.width.toFloat()
                        contentHeightPx = it.height.toFloat()
                    },
            ) {
                CircularTabIndicator(
                    selectedIndex = selectedIndex,
                    tabCount = tabCount,
                    contentWidthPx = contentWidthPx,
                    contentHeightPx = contentHeightPx,
                    color = scheme.primary.copy(alpha = 0.18f),
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppDestinations.entries.forEach { dest ->
                        FloatingNavItem(
                            destination = dest,
                            selected = selectedRoute == dest.route,
                            onClick = {
                                if (selectedRoute != dest.route) {
                                    navController.navigate(dest.route) {
                                        popUpTo(HOME_ROUTE) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

/** 圆形选中背景：按等分槽位做 index 位移动画。 */
@Composable
private fun CircularTabIndicator(
    selectedIndex: Int,
    tabCount: Int,
    contentWidthPx: Float,
    contentHeightPx: Float,
    color: Color,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { TabIndicatorSize.toPx() / 2f }
    val animatable = remember {
        Animatable(TabIndicatorMotion.index)
    }

    LaunchedEffect(selectedIndex) {
        animatable.animateTo(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
        TabIndicatorMotion.index = selectedIndex.toFloat()
    }

    DisposableEffect(Unit) {
        onDispose {
            TabIndicatorMotion.index = animatable.value
        }
    }

    if (contentWidthPx <= 0f || contentHeightPx <= 0f || tabCount <= 0) return

    val slotWidth = contentWidthPx / tabCount
    val centerX = (animatable.value + 0.5f) * slotWidth
    val centerY = contentHeightPx / 2f

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (centerX - radiusPx).roundToInt(),
                    (centerY - radiusPx).roundToInt(),
                )
            }
            .size(TabIndicatorSize)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun FloatingNavItem(
    destination: AppDestinations,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navTint",
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = destination.label,
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
    }
}

private fun Color.luminanceApprox(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.299f * r + 0.587f * g + 0.114f * b
}
