package com.fox.music.feature.profile.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.fox.music.core.ui.theme.InkQuaternary
import com.fox.music.core.ui.theme.InkTertiary
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.core.ui.theme.SeedSwatch
import com.fox.music.core.ui.theme.colorSchemeFromSeed
import com.fox.music.feature.profile.viewmodel.ThemeSettingsIntent
import com.fox.music.feature.profile.viewmodel.ThemeSettingsState
import com.fox.music.feature.profile.viewmodel.ThemeSettingsViewModel

const val THEME_SETTINGS_ROUTE = "theme_settings"

private val AmbienceOptions: List<Pair<Float, String>> = listOf(
    AppThemeIntensity.SOFT to "淡",
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

    // 「自定义色」抽屉默认收起（纯展示态，不参与草稿，所以不进 ThemeDraft）。
    var customColorExpanded by remember { mutableStateOf(false) }

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
                ThemeSectionKicker(
                    title = "预设",
                    modifier = pagePadding.padding(top = 20.dp),
                )
            }
            item {
                ThemeSwatchGrid(
                    swatches = state.seedSwatches,
                    selectedArgb = draft.seedArgb,
                    onSelectSwatch = { argb ->
                        draft = draft.copy(seedArgb = argb, custom = true)
                    },
                    modifier = pagePadding.padding(top = 10.dp),
                )
            }
            item {
                // 自定义色：默认折叠。整行可点，右侧 chevron 顺时针转 90°（› → ⌄）后
                // 取色盘向下展开。折叠是为了让「预设 → 浓度 → 深色模式」这条主路径不必
                // 先滚过 110dp 的面板 + 22dp 色相条。
                Column(modifier = pagePadding.padding(top = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable { customColorExpanded = !customColorExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "自定义色",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                letterSpacing = 2.sp,
                            ),
                            fontWeight = FontWeight.Medium,
                            color = InkQuaternary,
                        )
                        ExpandChevron(expanded = customColorExpanded)
                    }
                    AnimatedVisibility(
                        visible = customColorExpanded,
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = 220),
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = tween(durationMillis = 160)),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 200),
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = tween(durationMillis = 120)),
                    ) {
                        // 取色盘（v12）：拖拽即改草稿，HEX 只读展示。
                        FoxColorPicker(
                            color = Color(draft.seedArgb),
                            onColorChange = { color ->
                                draft = draft.copy(
                                    seedArgb = color.toArgb().toLong() and 0xFFFFFFFFL,
                                    custom = true,
                                )
                            },
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            presets = emptyList(),
                            showHex = true,
                        )
                    }
                }
            }
            item {
                ThemeSectionKicker(
                    title = "浓度",
                    modifier = pagePadding.padding(top = 20.dp),
                )
            }
            item {
                ThemeIntensitySeg(
                    intensity = draft.intensity,
                    onSelectIntensity = { value ->
                        draft = draft.copy(intensity = value, custom = true)
                    },
                    modifier = pagePadding.padding(top = 10.dp),
                )
            }
            item {
                Box(
                    modifier = pagePadding
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(foxHairline()),
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

/**
 * 刊头（设计稿④）：全大写眼标（强调色）+ 衬线 21sp「主题设置」。
 *
 * v12 之前是 APPEARANCE + 32sp「主题」，和歌词设置弹层（LYRICS + 21sp）不成套；
 * 两页现在共用同一套刊头尺度。
 */
@Composable
private fun ThemeMasthead(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "THEME",
            style = FoxEditorial.kicker.copy(
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                letterSpacing = 2.6.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "主题设置",
            style = FoxEditorial.mastheadTitle.copy(
                fontSize = 21.sp,
                lineHeight = 24.sp,
                letterSpacing = (-0.4).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** 分区眼标（设计稿④）：11sp 全大写 + 2sp 字距 + ink4，取代原来「01 配色 ——」的编号行。 */
@Composable
private fun ThemeSectionKicker(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 2.sp,
        ),
        fontWeight = FontWeight.Medium,
        color = InkQuaternary,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 展开箭头：手绘 chevron（两条 1.6dp 圆头线段），收起时朝右「›」，
 * 展开时顺时针转 90° 朝下「⌄」。
 *
 * 为什么不用 `Text("›")` 转：中文字体里的「›」是全角引号族字形，字号/基线随字体漂移，
 * 而且旋转后线宽会被抗锯齿拉粗；Canvas 画的线宽在任何密度下都是精确的 1.6dp。
 */
@Composable
private fun ExpandChevron(expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "customColorChevron",
    )
    val tint = InkQuaternary
    Canvas(
        modifier = Modifier
            .size(18.dp)
            .rotate(rotation),
    ) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.36f, h * 0.22f)
            lineTo(w * 0.64f, h * 0.5f)
            lineTo(w * 0.36f, h * 0.78f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
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
                    .padding(horizontal = 16.dp, vertical = 16.dp),
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
                        text = "稳稳的幸福",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                        ),
                        fontWeight = FontWeight.Bold,
                        color = previewScheme.onSurface,
                    )
                    Text(
                        text = "陈奕迅",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                        ),
                        color = foxInkMuted(darkTheme),
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                // 实心圆播放键：预览里「强调色直接上色块」的最强样本
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(previewScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.iv_play),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(previewScheme.onPrimary),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            // 三颗 chip：实心强调色 / 容器色 / 描边幽灵——一次把 primary、
            // primaryContainer、outline 三条通道同时摊开，浓度一档一档都有反应。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(previewScheme.primary)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "正在播放",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                        ),
                        fontWeight = FontWeight.Medium,
                        color = previewScheme.onPrimary,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(previewScheme.primaryContainer)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "沉浸",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                        ),
                        fontWeight = FontWeight.Medium,
                        color = previewScheme.onPrimaryContainer,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, foxHairline(darkTheme), RoundedCornerShape(20.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "无损",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                        ),
                        color = foxInkMuted(darkTheme),
                    )
                }
            }
            // 进度条：余量吃 primaryContainer —— 浓度档位在这里最直观
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(previewScheme.primaryContainer),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .fillMaxHeight()
                        .background(previewScheme.primary),
                )
            }
        }
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

/**
 * 「浓度」分段控件（设计稿④）：整条 variant 底 + 三格等宽，选中格铺 surface 实底 + 粗字。
 *
 * v12 之前是三颗各自描边的 pill，和自定义配色页的通道切换不是一套语言；现在两处统一成
 * 同一个分段控件，用户一眼能认出「这是在切换某一组里的一个」。
 */
@Composable
private fun ThemeIntensitySeg(
    intensity: Float,
    onSelectIntensity: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalized = AppThemeIntensity.normalize(intensity)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AmbienceOptions.forEach { (value, label) ->
            val selected = normalized == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .clickable { onSelectIntensity(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                    ),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        foxInkMuted()
                    },
                )
            }
        }
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
                    .height(40.dp)
                    .clip(shape)
                    .border(width = 1.dp, color = foxHairline(), shape = shape)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = foxInkMuted(),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onApply),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "应用",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
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
