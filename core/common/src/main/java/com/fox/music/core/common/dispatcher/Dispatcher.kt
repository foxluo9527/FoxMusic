package com.fox.music.core.common.dispatcher

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val foxDispatcher: FoxDispatcher)

enum class FoxDispatcher {
    Default,
    IO,
    Main,
    Unconfined
}
