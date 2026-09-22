package com.fox.music.core.common.dispatcher

/**
 * 协程调度器的逻辑标识，与平台实现解耦。
 *
 * 配套的 `@Dispatcher` 限定符注解依赖 `javax.inject.Qualifier`（JVM 专有 API），
 * 因此被下沉到 androidMain（`androidMain/.../dispatcher/Dispatcher.kt`）。
 * 本枚举则保持平台无关，可随 commonMain 一起被 iOS 复用。
 */
enum class FoxDispatcher {
    Default,
    IO,
    Main,
    Unconfined
}
