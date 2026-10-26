package com.fox.music.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Vinyl Night brand palette ──────────────────────────────────────────────
val VinylInk = Color(0xFF0E0C0A)
val VinylInkElevated = Color(0xFF1A1612)
val VinylInkSurface = Color(0xFF221C16)
val VinylInkVariant = Color(0xFF2E261E)
val VinylCream = Color(0xFFF3EDE3)
val VinylCreamMuted = Color(0xFFC9BFAF)
val VinylAmber = Color(0xFFE8A838)
val VinylAmberDeep = Color(0xFFC4841A)
val VinylAmberSoft = Color(0xFFF5D4A0)
val VinylCopper = Color(0xFFD4784A)
val VinylRose = Color(0xFFC96B6B)

// Light mode paper tones
// 2026-10 改版：纸感底色去掉过重黄调，改成更中性的暖灰（旧值 F7F1E8/FFFBF5/EDE4D6 偏米黄，
// 大面积铺开会让整屏发"黄"，也是"AI 味"的一部分）。层级靠 1dp 发丝线而不是靠色差。
val PaperBg = Color(0xFFF7F5F0)
val PaperSurface = Color(0xFFFFFDF9)
val PaperVariant = Color(0xFFEFE9DF)
val InkOnPaper = Color(0xFF1A1512)
val InkMuted = Color(0xFF5A544C)

// ── 语义色（浅底·纸感） ─────────────────────────────────────────────────
// 发丝线：分隔线 / 1dp 描边。比 outline 更浅，只做"分界"不做"框"。
val PaperHairline = Color(0xFFE4DDD1)
/** 说明文字 / 艺人名：5.0:1，AA 通过（旧值 #766F65 只有 4.43:1） */
val InkTertiary = Color(0xFF6F6860)
/** 占位符 / 极弱信息：不承载语义，只做视觉留白 */
val InkQuaternary = Color(0xFFA79F92)
/** 浅底唯一强调色：陶土暖橙，4.5:1，取代原来偏亮的琥珀 */
val AccentTerracotta = Color(0xFFB8513A)
/** 深底强调色：亮一档，磨砂黑上 4.9:1 */
val AccentTerracottaBright = Color(0xFFE2563C)
/** 磨砂黑：迷你播放条 / 音流沉浸底 */
val InkGlass = Color(0xFF17140F)

// Legacy primary palette (kept for FOX_PURPLE preset)
val Purple500 = Color(0xFF6C63FF)
val Purple700 = Color(0xFF5A52E0)
val Purple200 = Color(0xFFB0ACFF)

/**
 * 全站发丝线（深色自适应）。
 *
 * [PaperHairline] 是浅色专用常量，深色底上那抹 #E4DDD1 会变成一条刺眼亮线，
 * 弹层里尤其明显。凡是「同一根线要跟着主题变」的地方都用这个，别再直接引
 * [PaperHairline]。
 */
@Composable
fun foxHairline(): Color = if (isSystemInDarkTheme()) Color(0xFF3A322A) else PaperHairline

/** 同一根线在深色下要更弱一档时的选择（色值同 [foxHairline]，语义上表示「描边」而非「分隔」）。 */
@Composable
fun foxHairlineStroke(): Color = foxHairline()

val Pink500 = Color(0xFFFF6584)
val Pink700 = Color(0xFFE04E6B)
val Pink200 = Color(0xFFFFADBD)

val Teal500 = Color(0xFF4ECDC4)
val Teal700 = Color(0xFF3AB5AD)
val Teal200 = Color(0xFF9AE6E0)

val Gray50 = Color(0xFFFAFAFA)
val Gray100 = Color(0xFFF5F5F5)
val Gray200 = Color(0xFFEEEEEE)
val Gray300 = Color(0xFFE0E0E0)
val Gray400 = Color(0xFFBDBDBD)
val Gray500 = Color(0xFF9E9E9E)
val Gray700 = Color(0xFF616161)
val Gray800 = Color(0xFF424242)
val Gray900 = Color(0xFF212121)

val DarkSurface = Color(0xFF1C1B1F)
val DarkBackground = Color(0xFF121212)
val DarkSurfaceVariant = Color(0xFF2D2D30)

val ErrorRed = Color(0xFFCF6679)
val ErrorRedLight = Color(0xFFB00020)
