package com.fox.music.core.common.util

import timber.log.Timber

object Logger {
    fun d(tag: String, message: String) = Timber.tag(tag).d(message)
    fun i(tag: String, message: String) = Timber.tag(tag).i(message)
    fun w(tag: String, message: String) = Timber.tag(tag).w(message)
    fun e(tag: String, message: String, throwable: Throwable? = null) =
        throwable?.let { Timber.tag(tag).e(it, message) } ?: Timber.tag(tag).e(message)
}
