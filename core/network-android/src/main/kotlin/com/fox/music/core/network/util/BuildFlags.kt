package com.fox.music.core.network.util

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * 运行时可调试标志，用于替代 AGP 的 `BuildConfig.DEBUG`。
 *
 * 之所以要替代：KMP 模块不再生成 BuildConfig（它是 Android 专有产物）。
 * 语义上与 `BuildConfig.DEBUG` 等价 —— AGP 的 `BuildConfig.DEBUG` 本身就取自
 * 构建类型的 debuggable 标志，最终落到 manifest 的 `android:debuggable`，
 * 也就是 [ApplicationInfo.FLAG_DEBUGGABLE]。
 * 这样 debug / release 两种包各自的行为与迁移前一致，不需要区分构建变体。
 */
internal fun isDebuggable(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
