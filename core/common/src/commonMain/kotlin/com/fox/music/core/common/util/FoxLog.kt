package com.fox.music.core.common.util

/**
 * 跨端日志门面。
 *
 * 以前 commonMain 想要打日志只能退回平台侧（Android 用 Timber），导致可下沉的
 * 业务逻辑为了一行日志被迫留在 androidMain。这里给出 expect/actual，
 * Android 仍走 Timber，iOS 走 NSLog。
 */
expect object FoxLog {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
