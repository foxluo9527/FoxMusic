package com.fox.music.feature.profile.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.fox.music.core.ui.component.FoxColorPicker
import com.fox.music.core.ui.component.FoxEditorialRow
import com.fox.music.core.ui.component.FoxSwitch
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.theme.AppThemePresets
import com.fox.music.core.ui.theme.FoxEditorial
import com.fox.music.core.ui.theme.FoxMusicTypography
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.core.ui.theme.InkTertiary
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.core.ui.theme.SeedSwatch
import com.fox.music.core.ui.theme.colorSchemeFromSeed
import com.fox.music.feature.profile.viewmodel.ThemeSettingsIntent
import com.fox.music.feature.profile.viewmodel.ThemeSettingsState
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

/**
 * 页面草稿（v12 验收返工）。
 *
 * 主题页从「点一下立刻落盘 + recreate」改成「草稿 → 应用」：
 * - 配色 / 浓度 / 深色的任何改动都只在这里，绝不落盘、绝不重建 Activity；
 * - 草稿唯一的下游是预览卡的 colorScheme（见 [ThemeLivePreview]）；
 * - 只有点「应用」才 dispatch [ThemeSettingsIntent]，由 userPreferences flow 驱动全站重绘。
 *
 * [custom] = 是否走「按种子色派生整套强调色」这条路。预设主题本身没有种子色，
 * 所以一旦用户动了配色或浓度，就把当前生效的 primary 当作种子转进自定义分支 ——
 * 这样预览里看到的配色，落盘后就是最终配色，不会出现"预览一套、应用后另一套"。
 */
private data class ThemeDraft(
    val seedArgb: Long,
    val intensity: Float,
    val darkTheme: Boolean,
    val custom: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: ThemeSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val systemDark = isSystemInDarkTheme()

    // 落盘值 → 进入页面时的基准草稿。apply 之后落盘值变化，remember 的 key 变了，
    // 草稿自动回填成新的落盘值；编辑过程中落盘值不动，草稿不会被冲掉。
    val applied = remember(
        state.appThemeId,
        state.customThemeSeedArgb,
        state.themeIntensity,
        state.darkMode,
        systemDark,
    ) {
        appliedDraftOf(state = state, systemDark = systemDark)
    }
    var draft by remember(applied) { mutableStateOf(applied) }

    val onCancel: () -> Unit = {
        // 丢弃草稿，回到进入时的值
        draft = applied
        onBack()
    }
    val onApply: () -> Unit = {
        val seedChanged = draft.seedArgb != applied.seedArgb
        val intensityChanged = AppThemeIntensity.normalize(draft.intensity) !=
            AppThemeIntensity.normalize(applied.intensity)
        val darkChanged = draft.darkTheme != applied.darkTheme

        // 已转到自定义分支（动过配色或浓度）才写主题；只拨深色开关不该顺手改配色。
        if (draft.custom && (!applied.custom || seedChanged || intensityChanged)) {
            viewModel.sendIntent(ThemeSettingsIntent.SelectCustomHue(draft.seedArgb))
        }
        if (intensityChanged) {
            viewModel.sendIntent(ThemeSettingsIntent.SelectIntensity(draft.intensity))
        }
        if (darkChanged) {
            viewModel.sendIntent(ThemeSettingsIntent.SetDarkMode(draft.darkTheme))
        }
        draft = applied
        onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            // 设计稿①刊头自带主标题，顶栏只留返回键（标题留空）
            FoxTopBar(
                title = "",
                showBackButton = true,
                onBackClick = onCancel,
            )
        },
        bottomBar = {
            ThemeActionBar(onCancel = onCancel, onApply = onApply)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val pagePadding = Modifier.padding(horizontal = FoxSpacing.pagePadding)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                ThemeMasthead(modifier = pagePadding.padding(top = 4.dp))
            }
            item {
                ThemeLivePreview(
                    seedArgb = draft.seedArgb,
                    intensity = draft.intensity,
                    darkTheme = draft.darkTheme,
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
                    selectedArgb = draft.seedArgb,
                    onSelectSwatch = { argb ->
                        draft = draft.copy(seedArgb = argb, custom = true)
                    },
                    modifier = pagePadding.padding(top = 8.dp),
                )
            }
            item {
                // 取色盘（v12）：拖拽即改草稿，HEX 只读展示。预设卡在上面，
                // 这里不再重复一排色圆点。
                FoxColorPicker(
                    color = Color(draft.seedArgb),
                    onColorChange = { color ->
                        draft = draft.copy(
                            seedArgb = color.toArgb().toLong() and 0xFFFFFFFFL,
                            custom = true,
                        )
                    },
                    modifier = pagePadding.padding(top = 14.dp),
                    presets = emptyList(),
                    showHex = true,
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
                    intensity = draft.intensity,
                    onSelectIntensity = { value ->
                        draft = draft.copy(intensity = value, custom = true)
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
                FoxEditorialRow(
                    title = "深色模式",
                    trailing = {
                        FoxSwitch(
                            checked = draft.darkTheme,
                            onCheckedChange = { enabled ->
                                draft = draft.copy(darkTheme = enabled)
                            },
                        )
                    },
                )
            }
        }
    }
}

/** 落盘偏好 → 进入页面时的基准草稿。 */
private fun appliedDraftOf(state: ThemeSettingsState, systemDark: Boolean): ThemeDraft {
    val dark = when (state.darkMode) {
        DarkMode.DARK -> true
        DarkMode.LIGHT -> false
        DarkMode.FOLLOW_SYSTEM -> systemDark
    }
    val custom = state.appThemeId == AppThemeIds.CUSTOM
    val seed = if (custom) {
        state.customThemeSeedArgb
    } else {
        // 预设主题没有「种子色」这一层，取它当前实际生效的 primary 当种子：
        // 这样只调浓度也能转进派生分支，且派生出的浅色 primary 与预设完全一致。
        AppThemePresets.colorScheme(state.appThemeId, dark).primary.toArgb().toLong() and 0xFFFFFFFFL
    }
    return ThemeDraft(
        seedArgb = seed,
        intensity = state.themeIntensity,
        darkTheme = dark,
        custom = custom,
    )
}

/** 刊头（设计稿①）：全大写眼标 + 衬线 32sp 主标题。 */
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
    }
}

/**
 * 「活体预览」（设计稿①）：一块迷你应用界面 —— 顶部条用当前强调色实底，
 * 卡体是纸面，内含封面 / 曲名 / 播放字形，以及三个**吃 primaryContainer** 的元件：
 * 实心 chip、进度条余量、ghost 按钮。整块套一层由草稿派生的 colorScheme，
 * 所以换色 / 换浓度 / 换明暗，这里立刻是铺开后的样子。
 *
 * 为什么补这三个：预览原来只画了吃 primary 的元件，而浓度三档影响的恰恰是
 * primaryContainer —— 元件里没有容器色，浓度调到哪一档预览都"一点没变"（v12 驳回项）。
 */
@Composable
private fun ThemeLivePreview(
    seedArgb: Long,
    intensity: Float,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val previewScheme = colorSchemeFromSeed(
        seedArgb = seedArgb,
        darkTheme = darkTheme,
        intensity = intensity,
    )
    val cardShape = RoundedCornerShape(14.dp)
    MaterialTheme(
        colorScheme = previewScheme,
        typography = FoxMusicTypography,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(cardShape)
                // 发丝线走深色感知：深底上用 #3A322A，浅底用 PaperHairline
                .border(width = 1.dp, color = foxHairline(darkTheme), shape = cardShape)
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
                            color = foxInkMuted(darkTheme),
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
                // ① 实心 chip（primaryContainer 底 + onPrimaryContainer 字）
                // ③ ghost 按钮（primary 描边 + primary 字）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(previewScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "已收藏",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                            ),
                            fontWeight = FontWeight.Medium,
                            color = previewScheme.onPrimaryContainer,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .border(1.dp, previewScheme.primary, RoundedCornerShape(7.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "查看详情",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                            ),
                            fontWeight = FontWeight.Medium,
                            color = previewScheme.primary,
                        )
                    }
                }
                // ② 进度条余量（primaryContainer 底 + primary 已播段）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(previewScheme.primaryContainer),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .fillMaxHeight()
                            .background(previewScheme.primary),
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
 *
 * 点击只改草稿（v12）—— 不再逐次落盘，也不会重建 Activity。
 */
@Composable
private fun ThemeSwatchGrid(
    swatches: List<SeedSwatch>,
    selectedArgb: Long,
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
                    val isSelected = selectedArgb == swatch.argb
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
 * 底部动作条：取消 / 应用 两个等宽按钮。
 *
 * 取消丢弃草稿回到进入时的值；应用才把草稿 dispatch 成落盘 intent。
 */
@Composable
private fun ThemeActionBar(
    onCancel: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(foxHairline()),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FoxSpacing.pagePadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(shape)
                    .border(width = 1.dp, color = foxHairline(), shape = shape)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onApply),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "应用",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

// ── 深色感知的语义色（v10 验收返工） ─────────────────────────────────────
/**
 * 发丝线：深色下取设计稿的 #3A322A，浅色取 PaperHairline #E4DDD1。
 *
 * [dark] 默认跟随系统；预览卡要传草稿的明暗，否则拨深色开关时卡片边框不跟着走。
 */
@Composable
private fun foxHairline(dark: Boolean = isSystemInDarkTheme()): Color =
    if (dark) Color(0xFF3A322A) else PaperHairline

/**
 * 次级文字（眼标 / 元信息）色。
 *
 * 深色下上修到 #A99F92：设计稿深色 ink4 = #7E7568 在 #17140F 上只有 4.05:1，
 * 不过 WCAG AA；#A99F92 是 7.0:1。**这是对设计稿做的可访问性上修**，不是风格改动。
 */
@Composable
private fun foxInkMuted(dark: Boolean = isSystemInDarkTheme()): Color =
    if (dark) Color(0xFFA99F92) else InkTertiary
