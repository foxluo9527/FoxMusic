package com.fox.music.core.common.dispatcher

import javax.inject.Qualifier

/**
 * Hilt 限定符，用于区分不同调度器的注入点。
 *
 * 之所以放在 androidMain 而不是 commonMain：`javax.inject.Qualifier` 是 JVM 专有 API，
 * 一旦放进 commonMain，等模块新增 iOS target 后 commonMain 就无法编译。
 * iOS 侧的调度器抽象不依赖 Hilt，届时另行定义。
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val foxDispatcher: FoxDispatcher)
