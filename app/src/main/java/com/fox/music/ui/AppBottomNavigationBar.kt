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
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
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
import com.fox.music.feature.social.COMMUNITY_PLAZA_ROUTE
import com.fox.music.feature.stream.StreamRoutes
import kotlin.math.roundToInt

/** 底部 Tab 路由：仅这些页面内嵌导航栏，以参与 NavHost 转场与可预测返回动画。 */
val TAB_ROOT_ROUTES: Set<String> = AppDestinations.entries.map { it.route }.toSet()

private val TabIndicatorSize = 44.dp
private val CenterButtonSize = 44.dp

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
    COMMUNITY(COMMUNITY_PLAZA_ROUTE, "社区", Icons.Filled.Forum, Icons.Outlined.Forum),
    PROFILE(PROFILE_ROUTE, "我的", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
}

/** 底栏展示顺序：中央 [null] 位为「音流模式」内联圆钮。 */
private val BottomBarSlots: List<AppDestinations?> = listOf(
    AppDestinations.HOME,
    AppDestinations.DISCOVER,
    null,
    AppDestinations.COMMUNITY,
    AppDestinations.PROFILE,
)

@Composable
fun AppBottomNavigationBar(
    navController: NavHostController,
    selectedRoute: String,
    modifier: Modifier = Modifier,
    isStreamPlaying: Boolean = false,
    /** 音流页处于缓冲中（播放器 STATE_BUFFERING）时，中央圆钮展示旋转 loading 图标。 */
    isStreamBuffering: Boolean = false,
    /** 已在音流页时点击中央圆钮：切换播放/暂停（不再重复导航到同一目的地）。 */
    onStreamPlayPause: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val isStreamTab = selectedRoute == StreamRoutes.STREAM
    // 音流页在底栏里没有对应的 dest，选中态落到中央槽位，指示器才不会「跳空」。
    val selectedSlot = if (isStreamTab) {
        BottomBarSlots.indexOfFirst { it == null }
    } else {
        BottomBarSlots.indexOfFirst { it?.route == selectedRoute }
    }
    val slotCount = BottomBarSlots.size

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = FoxSpacing.navInset, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 悬浮底栏背景（裁切，不含中央突起按钮）
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
        )

        // 内容层：不裁切，使中央按钮可向上突起
        var contentWidthPx by remember { mutableFloatStateOf(0f) }
        var contentHeightPx by remember { mutableFloatStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .onSizeChanged {
                    contentWidthPx = it.width.toFloat()
                    contentHeightPx = it.height.toFloat()
                },
        ) {
            if (selectedSlot >= 0) {
                CircularTabIndicator(
                    selectedIndex = selectedSlot,
                    tabCount = slotCount,
                    contentWidthPx = contentWidthPx,
                    contentHeightPx = contentHeightPx,
                    color = scheme.primary.copy(alpha = 0.18f),
                )
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomBarSlots.forEach { dest ->
                    if (dest == null) {
                        StreamCenterButton(
                            onClick = {
                                // 已在音流页：该圆钮是当前唯一的播放控制入口，点击切换播放/暂停。
                                if (isStreamTab) {
                                    onStreamPlayPause()
                                } else {
                                    // 必须与其它 Tab 用同一套导航选项：只用 launchSingleTop 会把「音流」
                                    // 压在当前 Tab 之上（栈 [HOME, 我的, 音流]），随后从别的 Tab 用
                                    // popUpTo(HOME){saveState} + restoreState 回「我的」时，恢复出来的是
                                    // 整段被保存的栈，栈顶仍是「音流」→ 我的页显示音流内容。
                                    navController.navigate(StreamRoutes.STREAM) {
                                        popUpTo(HOME_ROUTE) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            isSelected = isStreamTab,
                            isPlaying = isStreamPlaying,
                            isBuffering = isStreamBuffering,
                        )
                    } else {
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

/** 中央「音流模式」圆钮：内联在底栏内（不再上浮），选中音流时显示播放/暂停态。 */
@Composable
private fun StreamCenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isPlaying: Boolean = false,
    /** 音流页缓冲中：展示旋转 loading 图标替代播放/暂停图标。 */
    isBuffering: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(CenterButtonSize)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(scheme.primary, scheme.tertiary),
                    ),
                )
                .border(width = 2.dp, color = scheme.surface, shape = CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected && isBuffering) {
                // 缓冲中：旋转 loading 图标（indeterminate 自动旋转），提示音流正在加载/缓冲。
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = scheme.onPrimary,
                )
            } else {
                // 已在音流页时，圆钮常驻显示播放/暂停态（暂停后显示 ▶ 而不是切回音流 Waves 图标），
                // 它同时是音流页唯一的播放控制入口，状态与图标必须一致。
                Icon(
                    imageVector = when {
                        !isSelected -> Icons.Filled.Waves
                        isPlaying -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = when {
                        !isSelected -> "音流模式"
                        isPlaying -> "音流播放中，点击暂停"
                        else -> "音流已暂停，点击播放"
                    },
                    modifier = Modifier.size(24.dp),
                    tint = scheme.onPrimary,
                )
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
