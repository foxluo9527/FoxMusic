package com.fox.music.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.fox.music.core.model.user.AppThemeIds
import com.fox.music.core.model.user.AppThemeIntensity
import com.fox.music.core.model.user.AppThemePreset
import kotlin.math.pow

/** 自定义配色的一支种子色：色值 + 中文名（设计稿①要求色圆点下方带色名）。 */
data class SeedSwatch(
    val argb: Long,
    val name: String,
)

// ── 深色纸感底（v10 设计稿令牌表） ─────────────────────────────────────────
/** 深色 bg #17140F：旧值 VinylInk #0E0C0A 过黑，大面积铺开会糊成一团。 */
private val DarkBackgroundPaper = Color(0xFF17140F)
/** 深色 surface #1E1A15（浮层：Sheet / 卡片）。 */
private val DarkSurfacePaper = Color(0xFF1E1A15)
/** 深色 variant #2A241D（次级填充 / 未选中轨道）。 */
private val DarkPaperVariant = Color(0xFF2A241D)
/** 亮强调色上的字色：用最深底，保证对比度。 */
private val DarkInkBase = Color(0xFF17140F)

object AppThemePresets {

    val catalog: List<AppThemePreset> = listOf(
        AppThemePreset(
            id = AppThemeIds.VINYL_NIGHT,
            name = "黑胶夜场",
            description = "深墨与暖琥珀 — FoxMusic 全新默认",
            previewColors = listOf(0xFFE8A838, 0xFF0E0C0A, 0xFFF3EDE3),
        ),
        AppThemePreset(
            id = AppThemeIds.FOX_PURPLE,
            name = "紫韵经典",
            description = "经典紫调（兼容旧版）",
            previewColors = listOf(0xFF6C63FF, 0xFFFF6584, 0xFF4ECDC4),
        ),
        AppThemePreset(
            id = AppThemeIds.YOUTHFUL,
            name = "青春洋溢",
            description = "活力橙与天空蓝",
            previewColors = listOf(0xFFFF6B6B, 0xFF4ECDC4, 0xFFFFE66D),
        ),
        AppThemePreset(
            id = AppThemeIds.NOBLE_CLASSICAL,
            name = "尊贵古典",
            description = "鎏金与酒红",
            previewColors = listOf(0xFFC9A227, 0xFF8B2942, 0xFF2C1810),
        ),
        AppThemePreset(
            id = AppThemeIds.OCEAN_BREEZE,
            name = "海洋清风",
            description = "蔚蓝与薄荷",
            previewColors = listOf(0xFF2196F3, 0xFF00BCD4, 0xFF80DEEA),
        ),
        AppThemePreset(
            id = AppThemeIds.FOREST_MIST,
            name = "森林薄雾",
            description = "翠绿与大地棕",
            previewColors = listOf(0xFF43A047, 0xFF8BC34A, 0xFF795548),
        ),
        AppThemePreset(
            id = AppThemeIds.SUNSET_WARM,
            name = "暮色暖阳",
            description = "落日橙与玫瑰粉",
            previewColors = listOf(0xFFFF7043, 0xFFFF8A65, 0xFFF06292),
        ),
    )

    /**
     * 12 支「作家色板」种子色（2026-10 v8 第二批改版）。
     *
     * 旧版给的是 12 个 Material 高饱和色（#E53935 / #FF6F00 …），配上 alpha 派生后
     * 在纸底上呈现脏粉 / 脏蓝。这里全部换成低饱和、有"颜料感"的色相，
     * 顺序按设计稿①的 4×3 网格：陶土 / 琥珀 / 苔绿 / 墨绿 / 青瓷 / 藏蓝 /
     * 黛蓝 / 绛紫 / 葡萄 / 玫瑰 / 焦糖 / 石墨。
     */
    val customSeedSwatches: List<SeedSwatch> = listOf(
        SeedSwatch(0xFFB8513A, "陶土"),
        SeedSwatch(0xFFB07624, "琥珀"),
        SeedSwatch(0xFF5F7A4E, "苔绿"),
        SeedSwatch(0xFF3E6B5C, "墨绿"),
        SeedSwatch(0xFF4E8C86, "青瓷"),
        SeedSwatch(0xFF38598C, "藏蓝"),
        SeedSwatch(0xFF44607E, "黛蓝"),
        SeedSwatch(0xFF7E4E60, "绛紫"),
        SeedSwatch(0xFF6B4E8C, "葡萄"),
        SeedSwatch(0xFFA85466, "玫瑰"),
        SeedSwatch(0xFF9C6B44, "焦糖"),
        SeedSwatch(0xFF4A4A4E, "石墨"),
    )

    /** 兼容旧调用方：只要色值不要名字。 */
    val customHueOptions: List<Long> = customSeedSwatches.map { it.argb }

    /** 按色值反查色名（自定义 HEX 时返回 null）。 */
    fun seedName(argb: Long): String? =
        customSeedSwatches.firstOrNull { it.argb == argb }?.name

    fun presetName(themeId: String): String =
        catalog.firstOrNull { it.id == themeId }?.name ?: "个性自定义"

    fun colorScheme(themeId: String, darkTheme: Boolean): ColorScheme {
        val schemes = if (darkTheme) darkSchemes else lightSchemes
        return schemes[themeId] ?: schemes.getValue(AppThemeIds.VINYL_NIGHT)
    }

    private val lightSchemes: Map<String, ColorScheme> = mapOf(
        AppThemeIds.VINYL_NIGHT to lightColorScheme(
            // 2026-10 改版：浅底强调色从偏亮的琥珀换成陶土暖橙（单一强调色原则），
            // 全站链接 / 选中态 / 进度都用它；琥珀只留给深色沉浸场景。
            primary = AccentTerracotta,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF3DCD5),
            onPrimaryContainer = Color(0xFF8E3A26),
            secondary = VinylCopper,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF5DCC8),
            onSecondaryContainer = Color(0xFF4A2814),
            tertiary = VinylRose,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFF5D5D5),
            onTertiaryContainer = Color(0xFF4A2020),
            background = PaperBg,
            onBackground = InkOnPaper,
            surface = PaperSurface,
            onSurface = InkOnPaper,
            surfaceVariant = PaperVariant,
            // 次级文字用 ink-3（5.0:1）：旧值在首页艺人名上只有 4.43:1，不过 AA
            onSurfaceVariant = InkTertiary,
            outline = Color(0xFFB8A990),
            error = ErrorRedLight,
            onError = Color.White,
        ),
        AppThemeIds.FOX_PURPLE to lightColorScheme(
            primary = Purple500,
            onPrimary = Color.White,
            primaryContainer = Purple200,
            onPrimaryContainer = Purple700,
            secondary = Pink500,
            onSecondary = Color.White,
            secondaryContainer = Pink200,
            onSecondaryContainer = Pink700,
            tertiary = Teal500,
            onTertiary = Color.White,
            tertiaryContainer = Teal200,
            onTertiaryContainer = Teal700,
            background = Gray50,
            onBackground = Gray900,
            surface = Color.White,
            onSurface = Gray900,
            surfaceVariant = Gray100,
            onSurfaceVariant = Gray700,
            outline = Gray400,
            error = ErrorRedLight,
            onError = Color.White,
        ),
        AppThemeIds.YOUTHFUL to lightColorScheme(
            primary = Color(0xFFFF6B6B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFD5D5),
            onPrimaryContainer = Color(0xFFC62828),
            secondary = Color(0xFF4ECDC4),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFB2EBF2),
            onSecondaryContainer = Color(0xFF00695C),
            tertiary = Color(0xFFFFE66D),
            onTertiary = Color(0xFF5D4037),
            tertiaryContainer = Color(0xFFFFF9C4),
            onTertiaryContainer = Color(0xFFF57F17),
            background = Color(0xFFFFFBF7),
            onBackground = Color(0xFF2D2D2D),
            surface = Color.White,
            onSurface = Color(0xFF2D2D2D),
            surfaceVariant = Color(0xFFFFF0EB),
            onSurfaceVariant = Color(0xFF757575),
            outline = Color(0xFFBDBDBD),
            error = ErrorRedLight,
            onError = Color.White,
        ),
        AppThemeIds.NOBLE_CLASSICAL to lightColorScheme(
            primary = Color(0xFFC9A227),
            onPrimary = Color(0xFF1A1208),
            primaryContainer = Color(0xFFF5E6B8),
            onPrimaryContainer = Color(0xFF5C4A10),
            secondary = Color(0xFF8B2942),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF0D0D8),
            onSecondaryContainer = Color(0xFF5C1528),
            tertiary = Color(0xFF2C1810),
            onTertiary = Color(0xFFF5E6D0),
            tertiaryContainer = Color(0xFFE8D5C4),
            onTertiaryContainer = Color(0xFF2C1810),
            background = Color(0xFFFAF6F0),
            onBackground = Color(0xFF1C1410),
            surface = Color(0xFFFFFDF8),
            onSurface = Color(0xFF1C1410),
            surfaceVariant = Color(0xFFF0EAE0),
            onSurfaceVariant = Color(0xFF5C5048),
            outline = Color(0xFFB8A898),
            error = ErrorRedLight,
            onError = Color.White,
        ),
        AppThemeIds.OCEAN_BREEZE to lightColorScheme(
            primary = Color(0xFF2196F3),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFBBDEFB),
            onPrimaryContainer = Color(0xFF0D47A1),
            secondary = Color(0xFF00BCD4),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFB2EBF2),
            onSecondaryContainer = Color(0xFF006064),
            tertiary = Color(0xFF80DEEA),
            onTertiary = Color(0xFF004D40),
            tertiaryContainer = Color(0xFFE0F7FA),
            onTertiaryContainer = Color(0xFF00838F),
            background = Color(0xFFF5FAFF),
            onBackground = Color(0xFF1A2332),
            surface = Color.White,
            onSurface = Color(0xFF1A2332),
            surfaceVariant = Color(0xFFE8F4FD),
            onSurfaceVariant = Color(0xFF546E7A),
            outline = Color(0xFFB0BEC5),
            error = ErrorRedLight,
            onError = Color.White,
        ),
        AppThemeIds.FOREST_MIST to lightColorScheme(
            primary = Color(0xFF43A047),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC8E6C9),
            onPrimaryContainer = Color(0xFF1B5E20),
            secondary = Color(0xFF8BC34A),
            onSecondary = Color(0xFF1B3300),
            secondaryContainer = Color(0xFFDCEDC8),
            onSecondaryContainer = Color(0xFF33691E),
            tertiary = Color(0xFF795548),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFD7CCC8),
            onTertiaryContainer = Color(0xFF3E2723),
            background = Color(0xFFF7FBF5),
            onBackground = Color(0xFF1B2E1B),
            surface = Color.White,
            onSurface = Color(0xFF1B2E1B),
            surfaceVariant = Color(0xFFE8F5E9),
            onSurfaceVariant = Color(0xFF558B2F),
            outline = Color(0xFFA5D6A7),
            error = ErrorRedLight,
            onError = Color.White,
        ),
        AppThemeIds.SUNSET_WARM to lightColorScheme(
            primary = Color(0xFFFF7043),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFCCBC),
            onPrimaryContainer = Color(0xFFBF360C),
            secondary = Color(0xFFFF8A65),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFE0B2),
            onSecondaryContainer = Color(0xFFE65100),
            tertiary = Color(0xFFF06292),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFF8BBD0),
            onTertiaryContainer = Color(0xFF880E4F),
            background = Color(0xFFFFFAF5),
            onBackground = Color(0xFF2E1A10),
            surface = Color.White,
            onSurface = Color(0xFF2E1A10),
            surfaceVariant = Color(0xFFFFF3E0),
            onSurfaceVariant = Color(0xFF8D6E63),
            outline = Color(0xFFFFCC80),
            error = ErrorRedLight,
            onError = Color.White,
        ),
    )

    private val darkSchemes: Map<String, ColorScheme> = mapOf(
        AppThemeIds.VINYL_NIGHT to darkColorScheme(
            primary = VinylAmber,
            onPrimary = Color(0xFF2A1C06),
            primaryContainer = VinylAmberDeep,
            onPrimaryContainer = VinylAmberSoft,
            secondary = VinylCopper,
            onSecondary = Color(0xFF2A1408),
            secondaryContainer = Color(0xFF5C3020),
            onSecondaryContainer = Color(0xFFF5DCC8),
            tertiary = VinylRose,
            onTertiary = Color(0xFF2A1010),
            tertiaryContainer = Color(0xFF5C2828),
            onTertiaryContainer = Color(0xFFF5D5D5),
            // 深色底色对齐设计稿令牌表（见 DarkBackgroundPaper 注释）
            background = DarkBackgroundPaper,
            onBackground = VinylCream,
            surface = DarkSurfacePaper,
            onSurface = VinylCream,
            surfaceVariant = DarkPaperVariant,
            onSurfaceVariant = VinylCreamMuted,
            outline = Color(0xFF6B5E4E),
            error = ErrorRed,
            onError = Gray900,
        ),
        AppThemeIds.FOX_PURPLE to DarkColorScheme,
        AppThemeIds.YOUTHFUL to darkColorScheme(
            primary = Color(0xFFFF8A80),
            onPrimary = Color(0xFF5C1010),
            primaryContainer = Color(0xFFC62828),
            onPrimaryContainer = Color(0xFFFFD5D5),
            secondary = Color(0xFF4DD0E1),
            onSecondary = Color(0xFF00363A),
            secondaryContainer = Color(0xFF00695C),
            onSecondaryContainer = Color(0xFFB2EBF2),
            tertiary = Color(0xFFFFF176),
            onTertiary = Color(0xFF3E2723),
            tertiaryContainer = Color(0xFFF57F17),
            onTertiaryContainer = Color(0xFFFFF9C4),
            background = Color(0xFF121212),
            onBackground = Color(0xFFE0E0E0),
            surface = Color(0xFF1C1B1F),
            onSurface = Color(0xFFE0E0E0),
            surfaceVariant = Color(0xFF2D2D30),
            onSurfaceVariant = Color(0xFFBDBDBD),
            outline = Color(0xFF757575),
            error = ErrorRed,
            onError = Gray900,
        ),
        AppThemeIds.NOBLE_CLASSICAL to darkColorScheme(
            primary = Color(0xFFE8C547),
            onPrimary = Color(0xFF1A1208),
            primaryContainer = Color(0xFF5C4A10),
            onPrimaryContainer = Color(0xFFF5E6B8),
            secondary = Color(0xFFE57399),
            onSecondary = Color(0xFF3E0A1A),
            secondaryContainer = Color(0xFF5C1528),
            onSecondaryContainer = Color(0xFFF0D0D8),
            tertiary = Color(0xFFD7B896),
            onTertiary = Color(0xFF1C1410),
            tertiaryContainer = Color(0xFF3E2723),
            onTertiaryContainer = Color(0xFFE8D5C4),
            background = Color(0xFF12100E),
            onBackground = Color(0xFFE8E0D8),
            surface = Color(0xFF1C1814),
            onSurface = Color(0xFFE8E0D8),
            surfaceVariant = Color(0xFF2C2420),
            onSurfaceVariant = Color(0xFFB8A898),
            outline = Color(0xFF6D5D4D),
            error = ErrorRed,
            onError = Gray900,
        ),
        AppThemeIds.OCEAN_BREEZE to darkColorScheme(
            primary = Color(0xFF90CAF9),
            onPrimary = Color(0xFF0D47A1),
            primaryContainer = Color(0xFF1565C0),
            onPrimaryContainer = Color(0xFFBBDEFB),
            secondary = Color(0xFF4DD0E1),
            onSecondary = Color(0xFF006064),
            secondaryContainer = Color(0xFF00838F),
            onSecondaryContainer = Color(0xFFB2EBF2),
            tertiary = Color(0xFF80DEEA),
            onTertiary = Color(0xFF004D40),
            tertiaryContainer = Color(0xFF00695C),
            onTertiaryContainer = Color(0xFFE0F7FA),
            background = Color(0xFF0D1117),
            onBackground = Color(0xFFE3F2FD),
            surface = Color(0xFF141A22),
            onSurface = Color(0xFFE3F2FD),
            surfaceVariant = Color(0xFF1E2A36),
            onSurfaceVariant = Color(0xFF90A4AE),
            outline = Color(0xFF546E7A),
            error = ErrorRed,
            onError = Gray900,
        ),
        AppThemeIds.FOREST_MIST to darkColorScheme(
            primary = Color(0xFF81C784),
            onPrimary = Color(0xFF1B5E20),
            primaryContainer = Color(0xFF2E7D32),
            onPrimaryContainer = Color(0xFFC8E6C9),
            secondary = Color(0xFFAED581),
            onSecondary = Color(0xFF33691E),
            secondaryContainer = Color(0xFF558B2F),
            onSecondaryContainer = Color(0xFFDCEDC8),
            tertiary = Color(0xFFBCAAA4),
            onTertiary = Color(0xFF3E2723),
            tertiaryContainer = Color(0xFF5D4037),
            onTertiaryContainer = Color(0xFFD7CCC8),
            background = Color(0xFF0E120E),
            onBackground = Color(0xFFE8F5E9),
            surface = Color(0xFF141A14),
            onSurface = Color(0xFFE8F5E9),
            surfaceVariant = Color(0xFF1E2A1E),
            onSurfaceVariant = Color(0xFFA5D6A7),
            outline = Color(0xFF558B2F),
            error = ErrorRed,
            onError = Gray900,
        ),
        AppThemeIds.SUNSET_WARM to darkColorScheme(
            primary = Color(0xFFFFAB91),
            onPrimary = Color(0xFFBF360C),
            primaryContainer = Color(0xFFE64A19),
            onPrimaryContainer = Color(0xFFFFCCBC),
            secondary = Color(0xFFFFCC80),
            onSecondary = Color(0xFFE65100),
            secondaryContainer = Color(0xFFF57C00),
            onSecondaryContainer = Color(0xFFFFE0B2),
            tertiary = Color(0xFFF48FB1),
            onTertiary = Color(0xFF880E4F),
            tertiaryContainer = Color(0xFFC2185B),
            onTertiaryContainer = Color(0xFFF8BBD0),
            background = Color(0xFF120E0C),
            onBackground = Color(0xFFFFF3E0),
            surface = Color(0xFF1C1612),
            onSurface = Color(0xFFFFF3E0),
            surfaceVariant = Color(0xFF2C2018),
            onSurfaceVariant = Color(0xFFFFCC80),
            outline = Color(0xFF8D6E63),
            error = ErrorRed,
            onError = Gray900,
        ),
    )
}

internal val DarkColorScheme = darkColorScheme(
    primary = Purple200,
    onPrimary = Purple700,
    primaryContainer = Purple700,
    onPrimaryContainer = Purple200,
    secondary = Pink200,
    onSecondary = Pink700,
    secondaryContainer = Pink700,
    onSecondaryContainer = Pink200,
    tertiary = Teal200,
    onTertiary = Teal700,
    tertiaryContainer = Teal700,
    onTertiaryContainer = Teal200,
    background = DarkBackground,
    onBackground = Gray200,
    surface = DarkSurface,
    onSurface = Gray200,
    surfaceVariant = DarkPaperVariant,
    onSurfaceVariant = Gray400,
    outline = Gray500,
    error = ErrorRed,
    onError = Gray900,
)

/** 预混：把 [a] 按 [fraction] 的比例混进 [b]，返回**不透明**实色。 */
internal fun mixColor(a: Color, b: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = a.red * f + b.red * (1f - f),
        green = a.green * f + b.green * (1f - f),
        blue = a.blue * f + b.blue * (1f - f),
        alpha = 1f,
    )
}

/** 变暗：[amount] = 混入黑的比例。darken(seed, 0.28) = seed*72% + 黑*28%。 */
internal fun darken(color: Color, amount: Float): Color =
    mixColor(color, Color.Black, 1f - amount.coerceIn(0f, 1f))

/** 变亮：[amount] = 混入白的比例。 */
internal fun lighten(color: Color, amount: Float): Color =
    mixColor(color, Color.White, amount.coerceIn(0f, 1f))

// ── 容器色可读性兜底（v12 验收返工） ──────────────────────────────────────
/**
 * 容器色上正文字的最低对比度。
 *
 * 取 4.6 而不是刚好 4.5：Compose 的 Color 通道是 float32，逐档混合后的舍入误差在
 * 第三位小数，卡在 4.50 边界上有可能实测落到 4.49。留 0.1 余量后，12 支色板 × 三档
 * 落到屏幕 8bit 后的实测最低值仍有 4.59:1（AA 要求 4.5:1）。
 */
private const val ContainerTextMinContrast = 4.6f

/** WCAG 2.1 相对亮度（sRGB 非线性分量先解 gamma）。 */
private fun relativeLuminance(color: Color): Float {
    fun channel(v: Float): Float =
        if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(color.red) +
        0.7152f * channel(color.green) +
        0.0722f * channel(color.blue)
}

/** WCAG 2.1 对比度：(L亮 + 0.05) / (L暗 + 0.05)，返回 1.0~21.0。 */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * 把 [foreground] 沿「更黑 / 更白」方向继续推进，直到与 [background] 的对比度 ≥ [target]。
 *
 * 为什么需要它：浓度三档拉开到 6% / 22% / 48% 后，浓郁档的 primaryContainer 已经是
 * 明确的实色（陶土种子下 #DDAA9D），而 onPrimaryContainer 原本固定是
 * `darken(seed, 0.28f)`（#843A2A）——两者实测只有 3.93:1，够不上 AA。
 * 这里按实际算出来的对比度逐档加深/提亮，既保住色相血缘，又保证任何种子色
 * （用户取色盘可能拖出任意色）在任何档位下都读得清。
 */
private fun ensureContrast(
    foreground: Color,
    background: Color,
    target: Float = ContainerTextMinContrast,
    towardWhite: Boolean,
): Color {
    if (contrastRatio(foreground, background) >= target) return foreground
    val anchor = if (towardWhite) Color.White else Color.Black
    repeat(14) { step ->
        val mixToAnchor = 0.10f + 0.06f * step
        val candidate = mixColor(foreground, anchor, 1f - mixToAnchor)
        if (contrastRatio(candidate, background) >= target) return candidate
    }
    return anchor
}

/**
 * 深色底上的强调色：把种子色的**明度**提到可读区间，同时**保住彩度**。
 *
 * 为什么不能直接用 [lighten]：lighten 是「seed × amount + 白 × (1-amount)」，本质是
 * 往里灌白，饱和度会被一起冲淡 —— 黛蓝 #44607E 经 lighten(0.22) 后是 #D3DAE6（近灰白），
 * 陶土 #B8513A 变成 #EFD9D4（脏粉白）。两者在 #17140F 上都读不出"这是强调色"。
 *
 * 这里改走 HSV：色相 H 原样保留，明度 V 抬到 [minValue]，饱和度 S 兜底到 [minSaturation]。
 * 陶土 → #DB6145（设计稿深色强调色 #E2563C 的同族解法，深底上 ≈5.1:1）；
 * 黛蓝 → #5395DB（≈5.9:1），都过 WCAG AA。
 */
private fun boostForDark(
    seed: Color,
    minValue: Float = 0.86f,
    minSaturation: Float = 0.62f,
): Color {
    val r = seed.red
    val g = seed.green
    val b = seed.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> ((((g - b) / delta) % 6f) + 6f) % 6f * 60f
        max == g -> (((b - r) / delta) + 2f) * 60f
        else -> (((r - g) / delta) + 4f) * 60f
    }
    val value = maxOf(max, minValue)
    val saturation = if (max == 0f) minSaturation else maxOf(delta / max, minSaturation)
    return hsvToColor(hue, saturation, value)
}

/** HSV → RGB（不透明实色）。 */
private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val chroma = value * saturation
    val sector = (((hue % 360f) + 360f) % 360f) / 60f
    val x = chroma * (1f - kotlin.math.abs((sector % 2f) - 1f))
    val m = value - chroma
    val (r, g, b) = when (sector.toInt()) {
        0 -> Triple(chroma, x, 0f)
        1 -> Triple(x, chroma, 0f)
        2 -> Triple(0f, chroma, x)
        3 -> Triple(0f, x, chroma)
        4 -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    return Color(red = r + m, green = g + m, blue = b + m)
}

/**
 * 由一支种子色派生整套强调色系统。
 *
 * 2026-10 v8 第二批改版：**禁用 alpha 透明叠加**，全部改成预混实色。
 * 旧实现是 `seed.copy(alpha = 0.18f)` —— 容器色是半透明的，合成到纸底 #F7F5F0
 * 之后得到的是脏粉 / 脏蓝（同一份 alpha 在不同底色上呈现不同颜色，完全不可控）。
 * 现在容器色 = seed 与纸面 #FFFDF9 的实色预混，所见即所得，且深色模式下也一致。
 *
 * 底色（background / surface / surfaceVariant / outline）永远是纸感中性值，
 * 色板只影响强调系统 —— 换主色不会让整屏发花。
 *
 * @param intensity 氛围强度：柔和 0.06 / 均衡 0.22 / 浓郁 0.48，决定容器色里 seed 的占比。
 *   上限放到 0.55 是因为浓郁档已经到 0.48 —— 旧值 0.32 会把它截断成 0.32，
 *   三档在派生结果上又回到"改了等于没改"（v12 验收驳回项）。
 */
fun colorSchemeFromSeed(
    seedArgb: Long,
    darkTheme: Boolean,
    intensity: Float = AppThemeIntensity.DEFAULT,
): ColorScheme {
    val seed = Color(seedArgb)
    val containerFraction = intensity.coerceIn(0.04f, 0.55f)
    // 次级容器比主容器再淡一档（均衡档下 ≈ 8%），保持层级而不是三个同强度的色块。
    val softContainerFraction = containerFraction * (8f / 12f)
    return if (darkTheme) {
        // 深色强调色：提亮但**保住彩度**（见 [boostForDark]）。旧的 lighten(seed, 0.22f)
        // 往里灌了 78% 的白，黛蓝 #44607E 会被冲成 #D3DAE6 —— 在深底上看着像灰色，
        // 完全失去"强调色"的语义。
        val primary = boostForDark(seed)
        val secondary = boostForDark(seed, minValue = 0.78f)
        val tertiary = mixColor(seed, VinylCreamMuted, 0.45f)
        val primaryContainer = mixColor(seed, DarkPaperVariant, containerFraction + 0.16f)
        darkColorScheme(
            primary = primary,
            onPrimary = DarkInkBase,
            primaryContainer = primaryContainer,
            // 浓郁档容器已经很"实"，固定 lighten(seed, 0.62f) 会掉到 AA 以下，这里按实测兜底
            onPrimaryContainer = ensureContrast(
                foreground = lighten(seed, 0.62f),
                background = primaryContainer,
                towardWhite = true,
            ),
            secondary = secondary,
            onSecondary = DarkInkBase,
            secondaryContainer = mixColor(seed, DarkPaperVariant, softContainerFraction + 0.12f),
            onSecondaryContainer = lighten(seed, 0.72f),
            tertiary = tertiary,
            onTertiary = DarkInkBase,
            tertiaryContainer = mixColor(tertiary, DarkPaperVariant, softContainerFraction + 0.12f),
            onTertiaryContainer = lighten(tertiary, 0.66f),
            // 深色底色对齐设计稿令牌（bg #17140F / surface #1E1A15 / variant #2A241D），
            // 旧值 #0E0C0A 过黑，页面上所有"卡片"都糊成一团黑。
            background = DarkBackgroundPaper,
            onBackground = VinylCream,
            surface = DarkSurfacePaper,
            onSurface = VinylCream,
            surfaceVariant = DarkPaperVariant,
            onSurfaceVariant = VinylCreamMuted,
            outline = Color(0xFF6B5E4E),
            error = ErrorRed,
            onError = Gray900,
        )
    } else {
        val secondary = mixColor(seed, Color(0xFF807A70), 0.82f)
        val tertiary = mixColor(seed, Color(0xFF8C6B8C), 0.55f)
        val primaryContainer = mixColor(seed, PaperSurface, containerFraction)
        lightColorScheme(
            primary = seed,
            onPrimary = Color.White,
            primaryContainer = primaryContainer,
            // 浓郁档（0.48）容器色变深，darken(seed, 0.28f) 在陶土种子下实测只有
            // 3.93:1，这里按实测继续加深到达标（≥4.6:1 兜底，AA 4.5 之上留余量），
            // 色相血缘不变。
            onPrimaryContainer = ensureContrast(
                foreground = darken(seed, 0.28f),
                background = primaryContainer,
                towardWhite = false,
            ),
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = mixColor(seed, PaperSurface, softContainerFraction),
            onSecondaryContainer = darken(secondary, 0.28f),
            tertiary = tertiary,
            onTertiary = Color.White,
            tertiaryContainer = mixColor(tertiary, PaperSurface, softContainerFraction),
            onTertiaryContainer = darken(tertiary, 0.28f),
            background = PaperBg,
            onBackground = InkOnPaper,
            surface = PaperSurface,
            onSurface = InkOnPaper,
            surfaceVariant = PaperVariant,
            onSurfaceVariant = InkTertiary,
            outline = Color(0xFFB8A990),
            error = ErrorRedLight,
            onError = Color.White,
        )
    }
}
