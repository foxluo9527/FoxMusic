package com.fox.music.core.common.util

import timber.log.Timber

actual object FoxLog {
    actual fun d(tag: String, message: String) = Timber.tag(tag).d(message)
    actual fun i(tag: String, message: String) = Timber.tag(tag).i(message)
    actual fun w(tag: String, message: String) = Timber.tag(tag).w(message)
    actual fun e(tag: String, message: String, throwable: Throwable?) =
        throwable?.let { Timber.tag(tag).e(it, message) } ?: Timber.tag(tag).e(message)
}
