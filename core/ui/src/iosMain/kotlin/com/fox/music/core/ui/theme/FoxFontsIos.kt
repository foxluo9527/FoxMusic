package com.fox.music.core.ui.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * iOS 侧字体：暂用系统字体族近似 —— Fraunces 是衬线，对应 Serif；
 * Manrope 是无衬线，对应系统默认（SF）。
 *
 * 后续若要求品牌字体完全一致，把 ttf 放进 commonMain/composeResources/font，
 * 两端改用 Font(Res.font.fraunces) 即可（Android 侧同步去掉 res/font）。
 */
actual val FoxDisplayFont: FontFamily = FontFamily.Serif
actual val FoxBodyFont: FontFamily = FontFamily.Default
