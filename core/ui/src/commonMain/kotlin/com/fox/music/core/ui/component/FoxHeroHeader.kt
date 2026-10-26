package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.ui.theme.FoxEditorial
import com.fox.music.core.ui.theme.InkGlass

/**
 * 编辑杂志风 · 全宽英雄区（v9 核心组件之一）。
 *
 * 全宽大图铺满顶部 + 渐变压暗 + 标题浮层（Apple Music 每日推荐那种），替代 v8
 * 的"居中圆角小卡片"。质感来自真实图/材质 + 渐变遮罩，不是色块。
 *
 * @param heroVisual 视觉底层（封面图 / 渐变），先绘制、铺满，再叠遮罩与内容。
 * @param scrimStrength 底部遮罩最深处的不透明度（0~1），默认 0.72。
 * @param kicker 全大写眼标（APPEARANCE / ARTIST / 头条 · 编辑精选）。
 * @param actions 浮层底部的操作行（播放全部 / 收藏 / 分享）。
 * @param contentColor 浮层文字色，默认白；深底英雄用白，浅底英雄可传 Ink。
 */
@Composable
fun FoxHeroHeader(
    heroVisual: @Composable BoxScope.() -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    height: Dp = 230.dp,
    scrimStrength: Float = 0.72f,
    kicker: String? = null,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    contentColor: Color = Color.White,
    contentPadding: PaddingValues = PaddingValues(start = 18.dp, end = 18.dp, bottom = 16.dp),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        // 1) 视觉底层（图 / 渐变）
        Box(modifier = Modifier.fillMaxSize(), content = heroVisual)

        // 2) 渐变压暗遮罩：上浅下深，保证底部浮层文字可读
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.04f),
                            InkGlass.copy(alpha = scrimStrength),
                        ),
                    ),
                ),
        )

        // 3) 浮层内容，贴底
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (kicker != null) {
                Text(
                    text = kicker.uppercase(),
                    style = FoxEditorial.kicker,
                    color = contentColor.copy(alpha = 0.82f),
                )
            }
            Text(
                text = title,
                style = FoxEditorial.heroTitle,
                color = contentColor,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = FoxEditorial.kicker.copy(
                        fontSize = 11.sp,
                        letterSpacing = 0.2.sp,
                    ),
                    color = contentColor.copy(alpha = 0.85f),
                )
            }
            if (actions != null) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}
