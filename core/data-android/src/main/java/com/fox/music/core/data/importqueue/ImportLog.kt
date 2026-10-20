package com.fox.music.core.data.importqueue

import com.fox.music.core.common.util.Logger

/** 歌单导入全链路中文日志（Logcat 过滤 tag：歌单导入） */
object ImportLog {
    const val TAG = "PlaylistImport"

    fun i(message: String) = Logger.i(TAG, message)
    fun w(message: String) = Logger.w(TAG, message)
    fun e(message: String, t: Throwable? = null) = Logger.e(TAG, message, t)
}
