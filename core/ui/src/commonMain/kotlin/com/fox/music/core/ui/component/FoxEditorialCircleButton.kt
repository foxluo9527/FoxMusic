package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fox.music.core.ui.theme.PaperHairline

/**
 * 编辑杂志风 · 圆形边框图标钮（v10）：搜索返回 / 帖子详情更多 / 助手历史与新建。
 *
 * 视觉直径 [size]（默认 32dp），但命中区撑到 44dp 以满足触控目标 ≥44dp 的硬规则。
 * 容器 = surface 实底 + 1px hairline 描边（不用 alpha 叠色，避免落在不同底色上出脏色）。
 *
 * @param content 线性 SVG 图标（Icons.Outlined.* 或自建 vector），尺寸由调用方控制在 17–19dp。
 */
@Composable
fun FoxEditorialCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .semantics { role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, PaperHairline, CircleShape),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }
}
