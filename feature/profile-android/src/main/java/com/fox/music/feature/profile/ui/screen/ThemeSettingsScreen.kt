package com.fox.music.feature.profile.ui.screen

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.common.R
import com.fox.music.core.model.user.AppThemeIds
import com.fox.music.core.model.user.AppThemeIntensity
import com.fox.music.core.model.user.DarkMode
import com.fox.music.core.ui.component.FoxColorCard
import com.fox.music.core.ui.component.FoxEditorialRow
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.theme.FoxEditorial
import com.fox.music.core.ui.theme.FoxMusicTypography
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.core.ui.theme.InkQuaternary
import com.fox.music.core.ui.theme.InkTertiary
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.core.ui.theme.SeedSwatch
import com.fox.music.core.ui.theme.previewColorScheme
import com.fox.music.feature.profile.viewmodel.ThemeSettingsEffect
import com.fox.music.feature.profile.viewmodel.ThemeSettingsIntent
import com.fox.music.feature.profile.viewmodel.ThemeSettingsViewModel

const val THEME_SETTINGS_ROUTE = "theme_settings"

private val AmbienceOptions: List<Pair<Float, String>> = listOf(
    AppThemeIntensity.SOFT to "柔",
    AppThemeIntensity.BALANCED to "中",
    AppThemeIntensity.RICH to "浓",
)

/**
 * 设计稿①「01 配色」的出列顺序：只取 9 支作家色板（12 支里挑掉重复色相的
 * 墨绿 / 藏蓝 / 玫瑰），3 列网格刚好铺满三行。
 */
private val ThemeSwatchOrder: List<String> = listOf(
    "陶土", "黛蓝", "苔绿", "绛紫", "琥珀", "焦糖", "青瓷", "石墨", "葡萄",
)

/** 每支色板的用途标签；选中态会被覆盖成「已选」（见 [ThemeSwatchGrid]）。 */
private val ThemeSwatchTags: Map<String, String> = mapOf(
    "陶土" to "默认", "黛蓝" to "清冷", "苔绿" to "自然", "绛紫" to "浪漫",
    "琥珀" to "暖", "焦糖" to "大地", "青瓷" to "清", "石墨" to "极简", "葡萄" to "雅",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: ThemeSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ThemeSettingsEffect.RecreateActivity -> {
                    (context as? Activity)?.recreate()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            // 设计稿①刊头自带主标题，顶栏只留返回键（标题留空）
            FoxTopBar(
                title = "",
                showBackButton = true,
                onBackClick = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val pagePadding = Modifier.padding(horizontal = FoxSpacing.pagePadding)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                ThemeMasthead(modifier = pagePadding.padding(top = 4.dp))
            }
            item {
                ThemeLivePreview(
                    appThemeId = state.appThemeId,
                    customThemeSeedArgb = state.customThemeSeedArgb,
                    themeIntensity = state.themeIntensity,
                    modifier = pagePadding.padding(top = 14.dp),
                )
            }
            item {
                ThemeSectionHeader(
                    number = "01",
                    title = "配色",
                    modifier = pagePadding.padding(top = 16.dp),
                )
            }
            item {
                ThemeSwatchGrid(
                    swatches = state.seedSwatches,
                    selectedArgb = state.customThemeSeedArgb,
                    isCustomTheme = state.appThemeId == AppThemeIds.CUSTOM,
                    // 当前真正生效的主色：预设主题（如黑胶夜场）也能在网格里标出对应色卡，
                    // 否则非自定义主题下 9 张卡全都没有选中态。
                    activePrimaryArgb = MaterialTheme.colorScheme.primary.toArgb(),
                    onSelectSwatch = { argb ->
                        viewModel.sendIntent(ThemeSettingsIntent.SelectCustomHue(argb))
                    },
                    modifier = pagePadding.padding(top = 8.dp),
                )
            }
            item {
                ThemeSectionHeader(
                    number = "02",
                    title = "浓度",
                    modifier = pagePadding.padding(top = 18.dp),
                )
            }
            item {
                ThemeIntensityRow(
                    intensity = state.themeIntensity,
                    onSelectIntensity = { value ->
                        viewModel.sendIntent(ThemeSettingsIntent.SelectIntensity(value))
                    },
                    modifier = pagePadding.padding(top = 8.dp),
                )
            }
            item {
                ThemeSectionHeader(
                    number = "03",
                    title = "模式",
                    modifier = pagePadding.padding(top = 18.dp),
                )
            }
            item {
                // 深色开关（设计稿①03）：接真实偏好 —— FOLLOW_SYSTEM 时按系统解析，
                // 拨动即写 DarkMode.DARK / LIGHT，主 Activity 的 darkTheme 直接读偏好流，
                // 所以整页立刻变深，退出重进也不会回滚。
                val systemDark = isSystemInDarkTheme()
                val darkChecked = when (state.darkMode) {
                    DarkMode.DARK -> true
                    DarkMode.LIGHT -> false
                    else -> systemDark
                }
                FoxEditorialRow(
                    title = "深色模式",
                    trailing = {
                        Switch(
                            checked = darkChecked,
                            onCheckedChange = { enabled ->
                                viewModel.sendIntent(ThemeSettingsIntent.SetDarkMode(enabled))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = Color.White,
                                // 轨道走语义色：深色下是 #2A241D，不再固定用浅色 variant
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    },
                )
            }
            item {
                ThemeHexEntry(
                    currentHex = hexOf(MaterialTheme.colorScheme.primary.toArgb()),
                    onApplyHex = { argb ->
                        viewModel.sendIntent(ThemeSettingsIntent.SelectCustomHue(argb))
                    },
                )
            }
            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 刊头（设计稿①）：全大写眼标 + 衬线 32sp 主标题 + 11.5sp 副标。 */
@Composable
private fun ThemeMasthead(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "APPEARANCE",
            style = FoxEditorial.kicker.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
            color = foxInkMuted(),
        )
        Text(
            text = "主题",
            style = FoxEditorial.mastheadTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "纸感底不变 · 只改强调系统",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
            ),
            color = foxInkMuted(),
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

/**
 * 「活体预览」（设计稿①新增）：一块迷你应用界面 —— 顶部条用当前强调色实底，
 * 卡体是纸面，内含封面 / 曲名 / 播放字形 + 两条次级色条。整块套一层
 * [previewColorScheme]，所以点到哪支色板，这里就变成哪套配色铺开后的样子。
 */
@Composable
private fun ThemeLivePreview(
    appThemeId: String,
    customThemeSeedArgb: Long,
    themeIntensity: Float,
    modifier: Modifier = Modifier,
) {
    val previewScheme = previewColorScheme(
        appThemeId = appThemeId,
        customThemeSeedArgb = customThemeSeedArgb,
        themeIntensity = themeIntensity,
    )
    val cardShape = RoundedCornerShape(14.dp)
    MaterialTheme(
        colorScheme = previewScheme,
        typography = FoxMusicTypography,
    ) {
        // accent-soft 一律预混实色（禁用 alpha 叠加）：surface 与 primary 按 12% 混合，
        // 等价于设计稿的 #F3E3DE，但会跟着当前主色走。
        val accentSoft = lerp(previewScheme.surface, previewScheme.primary, 0.12f)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(cardShape)
                // 发丝线走深色感知：深底上用 #3A322A，浅底用 PaperHairline
                .border(width = 1.dp, color = foxHairline(), shape = cardShape)
                .background(previewScheme.surface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(previewScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 10.5.sp,
                        lineHeight = 13.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = previewScheme.onPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "♪",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                    ),
                    color = previewScheme.onPrimary,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        lerp(previewScheme.primary, Color.White, 0.28f),
                                        lerp(previewScheme.primary, Color.Black, 0.18f),
                                    ),
                                ),
                            ),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "屋顶上的猫",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 16.sp,
                            ),
                            fontWeight = FontWeight.Bold,
                            color = previewScheme.onSurface,
                        )
                        Text(
                            text = "独立民谣",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                            ),
                            color = foxInkMuted(),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.iv_play),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(previewScheme.primary),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentSoft),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(previewScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

/** 分区标题（设计稿①）：衬线编号(accent) + 13sp 粗标题 + 右侧延伸发丝线。 */
@Composable
private fun ThemeSectionHeader(
    number: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number,
            style = FoxEditorial.sectionNumber.copy(fontSize = 12.5.sp, lineHeight = 15.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(foxHairline()),
        )
    }
}

/**
 * 「01 配色」：9 支作家色板的 3 列实底网格。卡面就是该主色本身（白字名 + 用途标签），
 * 选中卡走 FoxColorCard 的 2dp 内描边，标签同时换成「已选」。
 */
@Composable
private fun ThemeSwatchGrid(
    swatches: List<SeedSwatch>,
    selectedArgb: Long,
    isCustomTheme: Boolean,
    activePrimaryArgb: Int,
    onSelectSwatch: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = ThemeSwatchOrder.mapNotNull { name ->
        swatches.firstOrNull { it.name == name }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        ordered.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                row.forEach { swatch ->
                    // 选中判定两条路：① 自定义主题 → 比对已存的 seed；
                    // ② 预设主题 → 比对当前真正生效的 primary（预设主题下否则一张都不选中）
                    val isSelected = (isCustomTheme && selectedArgb == swatch.argb) ||
                        activePrimaryArgb == swatch.argb.toInt()
                    FoxColorCard(
                        name = swatch.name,
                        containerColor = Color(swatch.argb),
                        tag = if (isSelected) "已选" else ThemeSwatchTags[swatch.name],
                        selected = isSelected,
                        height = 88.dp,
                        onClick = { onSelectSwatch(swatch.argb) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 「02 浓度」：柔 / 中 / 浓三档等宽描边 pill，选中态 accent 描边 + accent 文字。 */
@Composable
private fun ThemeIntensityRow(
    intensity: Float,
    onSelectIntensity: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AmbienceOptions.forEach { (value, label) ->
            AmbiencePill(
                label = label,
                selected = AppThemeIntensity.normalize(intensity) == value,
                onClick = { onSelectIntensity(value) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 描边 pill：未选中 hairline 描边 + ink3 文字；选中 accent 描边 + accent 粗体文字。 */
@Composable
private fun AmbiencePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .clip(shape)
            // 底色走语义 surface：深色下是 #1E1A15，不再硬编码浅色纸面（旧值深底上白得刺眼）
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (selected) accent else foxHairline(),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else foxInkMuted(),
        )
    }
}

/**
 * 「03 模式」里的自定义色值行：右侧显示当前强调色 hex + ›，点开折叠的 HEX 输入。
 */
@Composable
private fun ThemeHexEntry(
    currentHex: String,
    onApplyHex: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hexExpanded by remember { mutableStateOf(false) }
    var hexInput by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        FoxEditorialRow(
            title = "自定义色值",
            onClick = { hexExpanded = !hexExpanded },
            trailing = {
                Text(
                    text = currentHex,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                    ),
                    color = foxInkFaint(),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                    ),
                    color = foxInkFaint(),
                )
            },
        )
        if (hexExpanded) {
            HexInputRow(
                value = hexInput,
                onValueChange = { raw ->
                    // 只接受 0-9 / A-F，最多 6 位
                    hexInput = raw
                        .uppercase()
                        .filter { it.isDigit() || it in 'A'..'F' }
                        .take(6)
                },
                onApply = { argb -> onApplyHex(argb) },
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * HEX 输入行：6 位 hex 解析，非法输入不生效（按钮保持禁用 + 文案提示）。
 * 支持带不带 `#`，合法后回填成规范大写形式。
 */
@Composable
private fun HexInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onApply: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalized = value.trim().removePrefix("#")
    val parsed = remember(normalized) { parseHexColor(normalized) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                placeholder = {
                    Text(
                        text = "例如 B8513A",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = PaperHairline,
                ),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (parsed != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable(enabled = parsed != null) {
                        val argb = parsed
                        if (argb != null) onApply(argb)
                    }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "应用",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = if (parsed != null) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                )
            }
        }
        if (value.isNotEmpty() && parsed == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "需要 6 位十六进制色值（0-9 / A-F）",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ── 深色感知的语义色（v10 验收返工） ─────────────────────────────────────
/** 发丝线：深色下取设计稿的 #3A322A，浅色取 PaperHairline #E4DDD1。 */
@Composable
private fun foxHairline(): Color =
    if (isSystemInDarkTheme()) Color(0xFF3A322A) else PaperHairline

/**
 * 次级文字（眼标 / 副标 / 元信息）色。
 *
 * 深色下上修到 #A99F92：设计稿深色 ink4 = #7E7568 在 #17140F 上只有 4.05:1，
 * 不过 WCAG AA；#A99F92 是 7.0:1。**这是对设计稿做的可访问性上修**，不是风格改动。
 */
@Composable
private fun foxInkMuted(): Color =
    if (isSystemInDarkTheme()) Color(0xFFA99F92) else InkTertiary

/** 占位级文字（hex 值 / › 指示）：深色下 #8A8175（≈5.3:1）。 */
@Composable
private fun foxInkFaint(): Color =
    if (isSystemInDarkTheme()) Color(0xFF8A8175) else InkQuaternary

/** 0xAARRGGBB → "#RRGGBB"（大写）。 */
private fun hexOf(argb: Int): String =
    "#" + (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** 解析 6 位 hex → 0xAARRGGBB；非法返回 null（调用方据此禁用「应用」）。 */
private fun parseHexColor(raw: String): Long? {
    if (raw.length != 6) return null
    if (raw.any { !(it.isDigit() || it in 'A'..'F' || it in 'a'..'f') }) return null
    val rgb = raw.toLongOrNull(16) ?: return null
    return 0xFF000000L or rgb
}
