package com.fox.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.feature.discover.DISCOVER_ROUTE
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.profile.ui.screen.PROFILE_ROUTE
import com.fox.music.feature.social.COMMUNITY_PLAZA_ROUTE
import com.fox.music.feature.stream.StreamRoutes
import com.fox.music.navigation.navigateToTab

/** 底部 Tab 路由：仅这些页面内嵌导航栏，以参与 NavHost 转场与可预测返回动画。 */
val TAB_ROOT_ROUTES: Set<String> = AppDestinations.entries.map { it.route }.toSet()

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

/** 底栏展示顺序：中央 [null] 位为「音流模式」。 */
private val BottomBarSlots: List<AppDestinations?> = listOf(
    AppDestinations.HOME,
    AppDestinations.DISCOVER,
    null,
    AppDestinations.COMMUNITY,
    AppDestinations.PROFILE,
)

/**
 * 底部导航栏（原型 .tabbar：通栏 64dp + 顶部 1px hairline，不再悬浮）。
 *
 * 2026-10 二轮修订（设计师审查）：悬浮圆角岛 + 投影让底栏成了「第三处重阴影」，
 * 且与 24dp 内容轴脱节。改回通栏：白 93% 底 + 1dp #E4DDD1 顶线，无圆角无投影。
 * 选中态收敛为 ink 单色（图标实心 + 3dp 墨点）——accent 只留给进度/正在播放，
 * 不再让底栏与「选中」抢职责。未选中图标用 ink-3（5.0:1），过 UI 组件 3:1 门槛。
 */
@Composable
fun AppBottomNavigationBar(
    navController: NavHostController,
    selectedRoute: String,
    modifier: Modifier = Modifier,
    isStreamPlaying: Boolean = false,
    /** 音流页处于缓冲中（播放器 STATE_BUFFERING）时，中央图标展示旋转 loading。 */
    isStreamBuffering: Boolean = false,
    /** 已在音流页时点击中央槽位：切换播放/暂停（不再重复导航到同一目的地）。 */
    onStreamPlayPause: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val isStreamTab = selectedRoute == StreamRoutes.STREAM
    // 背景必须画在 navigationBarsPadding 之前：否则手势条区域露出无遮挡的内容
    //（设计师审查 P0：底栏下方一条透明带，列表文字以全对比度穿透）。
    // 音流页保持全透明（封面沉浸式延伸到手势条）。
    val barBackground = if (isStreamTab) {
        Color.Transparent
    } else if (scheme.background.luminanceApprox() < 0.5f) {
        scheme.surface
    } else {
        // 白 93%：设计稿 rgba(255,255,255,.93) + blur；Compose 侧用近实心底近似。
        scheme.surface.copy(alpha = 0.93f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBackground)
            .navigationBarsPadding(),
    ) {
        // 顶部 1dp 发丝线：替代投影表达「这条栏浮在内容上」；音流页沉浸式，线也隐去
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PaperHairline.copy(alpha = if (isStreamTab) 0f else 0.8f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomBarSlots.forEach { dest ->
                if (dest == null) {
                    StreamCenterButton(
                        onClick = {
                            // 已在音流页：该槽位是当前唯一的播放控制入口，点击切换播放/暂停。
                            if (isStreamTab) {
                                onStreamPlayPause()
                            } else {
                                // 必须与其它 Tab 用同一套导航选项：只用 launchSingleTop 会把「音流」
                                // 压在当前 Tab 之上（栈 [HOME, 我的, 音流]），随后从别的 Tab 用
                                // popUpTo(HOME){saveState} + restoreState 回「我的」时，恢复出来的是
                                // 整段被保存的栈，栈顶仍是「音流」→ 我的页显示音流内容。
                                navController.navigateToTab(StreamRoutes.STREAM)
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
                    BottomNavItem(
                        destination = dest,
                        selected = selectedRoute == dest.route,
                        onClick = {
                            if (selectedRoute != dest.route) {
                                navController.navigateToTab(dest.route)
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

/** 音流槽位：与其它槽位同构（图标 + 选中圆点），选中时图标切换成 ⏸ / ▶。 */
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
    // 音流页是深色封面沉浸背景，选中态 tint 用白色（图标/圆环/指示点同色）
    val tint by animateColorAsState(
        targetValue = if (isSelected) Color.White else scheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "streamTint",
    )
    NavSlot(
        modifier = modifier,
        onClick = onClick,
        selected = isSelected,
        selectedIndicatorColor = if (isSelected) Color.White else Color.Transparent,
    ) {
        // 音流页中央槽位是唯一的播放控制入口：图标套 36dp 圆环突出可点性
        //（用户反馈：选中态光秃秃的播放/暂停/缓冲图标太小，不知道能不能点）。
        val ring = if (isSelected) {
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(1.2.dp, tint.copy(alpha = 0.55f), CircleShape)
        } else {
            Modifier.size(36.dp)
        }
        Box(ring, contentAlignment = Alignment.Center) {
            when {
                isSelected && isBuffering -> CircularProgressIndicator(
                    modifier = Modifier.size(21.dp),
                    strokeWidth = 2.dp,
                    color = tint,
                )
                // 已在音流页时，图标常驻显示播放/暂停态（暂停后显示 ▶ 而不是切回 Waves 图标），
                // 它同时是音流页唯一的播放控制入口，状态与图标必须一致。
                isSelected && isPlaying -> Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "音流播放中，点击暂停",
                    modifier = Modifier.size(22.dp),
                    tint = tint,
                )
                isSelected -> Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "音流已暂停，点击播放",
                    modifier = Modifier.size(22.dp),
                    tint = tint,
                )
                else -> Icon(
                    imageVector = Icons.Outlined.Waves,
                    contentDescription = "音流模式",
                    modifier = Modifier.size(23.dp),
                    tint = tint,
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    destination: AppDestinations,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        targetValue = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navTint",
    )
    NavSlot(
        modifier = modifier,
        onClick = onClick,
        selected = selected,
        selectedIndicatorColor = scheme.onSurface,
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = destination.label,
            modifier = Modifier.size(23.dp),
            tint = tint,
        )
    }
}

/** 底栏槽位外壳：图标居中 + 选中态下方 3dp 圆点（默认 ink，音流页传白）。 */
@Composable
private fun NavSlot(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    selected: Boolean,
    selectedIndicatorColor: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) { content() }
            Spacer(modifier = Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(if (selected) selectedIndicatorColor else Color.Transparent),
            )
        }
    }
}

private fun Color.luminanceApprox(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.299f * r + 0.587f * g + 0.114f * b
}
