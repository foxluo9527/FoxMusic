package com.fox.music.core.ui.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * 跨端异步图片加载的最小区划。
 *
 * 为什么要抽象：图片加载库是平台绑定的 —— Android 用 Coil（依赖 OkHttp/Uri/Context），
 * iOS 端只能用 Ktor + Skia 自行解码。如果不抽象，CachedImage 就得下沉到 androidMain，
 * 而它被 14 个核心列表组件引用，会把整个组件库级联拖进 Android 专属源码集。
 *
 * 两端只需保证「加载中 / 失败 / 成功」三态回调语义一致，UI 层完全复用。
 */
@Composable
internal expect fun PlatformAsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    onError: () -> Unit,
    loadingContent: @Composable BoxScope.() -> Unit,
    errorContent: @Composable BoxScope.() -> Unit,
)

/**
 * 本地**绝对路径**图片（聊天设置页设置的聊天背景用）。
 *
 * 与 [PlatformAsyncImage] 的区别：数据源是设备本地文件而不是网络 URL ——
 * Android 走 Coil 的 File model，iOS 直接 NSFileManager 读字节后 Skia 解码。
 */
@Composable
expect fun PlatformFileImage(
    filePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
)
