package com.fox.music.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * 统一开关（v12 新增）。
 *
 * 为什么不用 Material3 的 [androidx.compose.material3.Switch]：M3 开关在纸感浅底上
 * 关闭态是「surfaceVariant 轨道 + 纯白拇指」，两个都很亮，看起来像一块没对齐的白色
 * 圆贴纸（歌词设置页实测被驳回就是因为这个）。这里自绘 40×24 的窄轨道：
 *
 * - 关：轨道走发丝线灰（深色 #3A322A），拇指近纸白 + 1dp 描边，克制且贴底；
 * - 开：轨道填充主题强调色，拇指纯白，无描边；
 * - 拇指 18dp、内缩 3dp，位移用 [animateDpAsState] 走 180ms，跟手不迟钝。
 *
 * 全站开关必须走这里，不要再单独写 `Switch(...)`。
 */
@Composable
fun FoxSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dark = isSystemInDarkTheme()
    val accent = MaterialTheme.colorScheme.primary
    val trackOff = if (dark) Color(0xFF3A322A) else Color(0xFFE4DDD1)
    val thumbOff = if (dark) Color(0xFFC9BFAF) else Color(0xFFFFFDF9)
    val thumbRing = if (dark) Color(0xFF6A6055) else Color(0xFFD5CEC2)

    val trackColor by animateColorAsState(
        targetValue = if (checked) accent else trackOff,
        animationSpec = tween(180),
        label = "foxSwitchTrack",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) Color.White else thumbOff,
        animationSpec = tween(180),
        label = "foxSwitchThumb",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 19.dp else 3.dp,
        animationSpec = tween(180),
        label = "foxSwitchOffset",
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(width = 40.dp, height = 24.dp)
            .clip(CircleShape)
            .background(if (enabled) trackColor else trackColor.copy(alpha = 0.5f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 0.dp)
                .offset(x = thumbOffset)
                .size(18.dp)
                .shadow(elevation = 1.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(if (enabled) thumbColor else thumbColor.copy(alpha = 0.6f))
                .then(
                    if (checked) Modifier else Modifier.border(1.dp, thumbRing, CircleShape)
                ),
        )
    }
}
