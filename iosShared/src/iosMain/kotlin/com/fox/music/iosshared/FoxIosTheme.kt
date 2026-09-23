package com.fox.music.iosshared

/**
 * iOS 宿主补色用的主题常量。
 *
 * iOS 侧目前**没有套 FoxMusicTheme**（共享 Compose 页面跑在 Material3 默认 light
 * colorScheme 上），所以 `MaterialTheme.colorScheme.background` 就是 M3 baseline 的
 * `#FEF7FF`。Swift 侧用它给状态栏/顶部安全区补同色底，消除"页面是浅紫、状态栏是白"
 * 的割裂感。
 *
 * TODO: iOS 接入 FoxMusicTheme（按用户的 appThemeId 选预设）后，这里应改为
 * `AppThemePresets.colorScheme(themeId, isDarkTheme).background`，不要留硬编码。
 */
object FoxIosTheme {

    /** 页面底色，ARGB（0xAARRGGBB）——与 Compose 侧 colorScheme.background 一致。 */
    fun pageBackgroundArgb(): Long = 0xFFFEF7FFL
}
