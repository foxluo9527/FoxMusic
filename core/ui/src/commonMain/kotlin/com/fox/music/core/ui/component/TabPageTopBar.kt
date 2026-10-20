package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fox.music.core.ui.theme.FoxBodyFont

/**
 * 主 Tab 页统一顶部栏：左上角三横（打开全局抽屉）、居中标题（不加粗）、右侧可选动作插槽。
 *
 * 标题用 [Box] 绝对居中，左侧三横与右侧动作不参与居中计算，保证任何情况下标题都居中。
 */
@Composable
fun TabPageTopBar(
    title: String,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 三横的视觉中心对齐「我的」页的头部图标（36dp 按钮 + 12dp 起始内边距 = 中心 30dp），
            // 本组件按钮 40dp，故左右内边距取 10dp。
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .background(scheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "更多",
                tint = scheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }

        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp),
            // 用正文字体（Manrope）+ Normal 字重：品牌展示字体 Fraunces 的变量字体默认 wght=900，
            // 视觉上始终是 Black 字重，无法真正“不加粗”，故标题改用正文无衬线字体。
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FoxBodyFont,
                fontWeight = FontWeight.Normal,
            ),
            color = scheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            // 固定与左侧三横按钮同为 40dp 的内容高度：否则右侧动作里的 48dp IconButton
            // 会把整条顶部栏撑高，左侧三横被垂直居中后在各 Tab 之间上下跳动。
            modifier = Modifier.align(Alignment.CenterEnd).height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/**
 * 让顶部栏横向“出血”到整屏宽度，修正各 Tab 左上角三横位置不一致的问题。
 *
 * 首页/发现/社区把 [TabPageTopBar] 放在带水平 contentPadding 的列表/网格里，三横会被容器
 * 内边距整体推右；「我的」页则贴边排布。传入所在槽位的左内边距 [slotInset] 后，本修饰符
 * 会把顶部栏量到整屏宽度并左移到屏幕边缘，使左上角图标与居中标题在所有 Tab 完全对齐。
 */
@Composable
fun Modifier.tabTopBarFullBleed(slotInset: Dp): Modifier {
    val insetPx = with(LocalDensity.current) { slotInset.roundToPx() }
    return this.layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            val fullWidth = constraints.maxWidth + insetPx * 2
            val placeable = measurable.measure(
                constraints.copy(minWidth = fullWidth, maxWidth = fullWidth),
            )
            // 对外仍上报槽位宽度，容器布局不受影响；内部左移回到屏幕边缘。
            layout(constraints.maxWidth, placeable.height) { placeable.place(x = -insetPx, y = 0) }
        }
    }
}
