package com.fox.music.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * 编辑杂志风 · 排版约定（v9）。
 *
 * 复用已落地的 Fraunces 衬线展示字体（[FoxDisplayFont]）与 Manrope 正文
 * （[FoxBodyFont]），不引入新字体资源。下面是一组"杂志感"排版尺度的快捷方式，
 * 供各页面统一刊头 / 分区标签 / 英雄标题使用，避免每处手写 TextStyle 导致不统一。
 */
object FoxEditorial {

    /** 刊头大标题：Fraunces 32sp，紧排 -1.2sp，粗体。这是"杂志"而非"列表"的第一信号。 */
    val mastheadTitle: TextStyle
        @Composable get() = TextStyle(
            fontFamily = FoxDisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 34.sp,
            letterSpacing = (-1.2).sp,
        )

    /** 英雄标题：Fraunces 30sp，用于艺人 / 广场头条浮层。 */
    val heroTitle: TextStyle
        @Composable get() = TextStyle(
            fontFamily = FoxDisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.5).sp,
        )

    /** 文章标题：Fraunces 22sp，用于帖子详情正文标题。 */
    val articleTitle: TextStyle
        @Composable get() = TextStyle(
            fontFamily = FoxDisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.6).sp,
        )

    /** 全大写 kicker：Manrope 10sp，字距 3sp，弱化色。分区 / 英雄眼标的统一写法。 */
    val kicker: TextStyle
        @Composable get() = TextStyle(
            fontFamily = FoxBodyFont,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            letterSpacing = 3.sp,
        )

    /** 分区编号（01 / 02）：Fraunces，强调色，落在发丝线左侧。 */
    val sectionNumber: TextStyle
        @Composable get() = TextStyle(
            fontFamily = FoxDisplayFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
        )

    /** 列表行序号（衬线灰）：用于无边框编辑列表左边的 01/02/03 概念序号。 */
    val listNumber: TextStyle
        @Composable get() = TextStyle(
            fontFamily = FoxDisplayFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 18.sp,
        )
}

/**
 * 由主色生成一张"英雄区"渐变底（径向 + 对角叠色），用于没有封面图时的占位英雄区。
 * 比纯色块高级：模拟真实材质的光照，而不是死板的色板。
 */
fun foxHeroGradient(seed: Color): Brush = Brush.radialGradient(
    // 起点偏移让高光偏右上，符合 Apple Music 类英雄图的光影习惯
    center = androidx.compose.ui.geometry.Offset(0.7f, 0.1f),
    radius = 1.2f,
    colors = listOf(
        lighten(seed, 0.18f),
        seed,
        darken(seed, 0.45f),
    ),
)
