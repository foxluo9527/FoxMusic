@file:OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package com.fox.music.iosshared

import com.fox.music.core.common.util.FoxLog
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import kotlin.native.setUnhandledExceptionHook

/**
 * 全局未捕获异常落盘（Kotlin/Native 最后一道防线）。
 *
 * 协程层的异常由 [foxMainScope] 挂的 CoroutineExceptionHandler 拦截；万一仍有
 * 漏网的未捕获异常（K/N 默认行为是 terminateWithUnhandledException → abort，
 * 进程卡死且什么都不留），这个 hook 会在终止前把异常类型 + 堆栈追加写入
 * `Application Support/fox_crash.log`，并通过 NSLog 输出到系统日志。
 *
 * 注意：hook **阻止不了终止**，只负责留证据。App 启动时调一次 [install]，
 * 出现卡死/闪退后来不及看控制台时，重新启动后读 [crashLog] 即可定位。
 */
object FoxCrashLog {

    private const val TAG = "FoxCrashLog"
    private const val FILE_NAME = "fox_crash.log"

    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        setUnhandledExceptionHook { throwable ->
            val stamp = kotlinx.datetime.Clock.System.now().toString()
            val payload = "\n===== $stamp =====\n" +
                "${throwable::class.simpleName}: ${throwable.message}\n" +
                throwable.stackTraceToString()
            // 系统日志：simctl spawn log show / Xcode 控制台都能看到
            FoxLog.e(TAG, "未捕获异常，进程即将终止:\n$payload", throwable)
            appendToFile(payload)
        }
        FoxLog.i(TAG, "crash log hook installed -> ${crashFilePath() ?: "(目录不可用)"}")
    }

    /** 读取累计的崩溃日志（无则 null）。供 Swift「设置/反馈」或调试时展示。 */
    @OptIn(ExperimentalForeignApi::class)
    fun crashLog(): String? = crashFilePath()?.let { path ->
        runCatching {
            val data = NSFileManager.defaultManager.contentsAtPath(path) ?: return@runCatching null
            val ptr = data.bytes ?: return@runCatching null
            ptr.reinterpret<ByteVar>().readBytes(data.length.toInt()).decodeToString()
        }.getOrNull()
    }

    fun clearCrashLog() {
        crashFilePath()?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun appendToFile(text: String) {
        val path = crashFilePath() ?: return
        runCatching {
            // "a" 追加：多次崩溃都保留；hook 里只做一次极简写入，避免重入风险
            val file = fopen(path, "a") ?: return@runCatching
            try {
                fputs(text, file)
            } finally {
                fclose(file)
            }
        }
    }

    private fun crashFilePath(): String? = runCatching {
        NSFileManager.defaultManager.URLForDirectory(
            NSApplicationSupportDirectory, NSUserDomainMask, null, true, null,
        )?.path?.let { dir ->
            // 目录已存在（FoxLocalChatStore 等也会创建），这里只拼路径
            "$dir/$FILE_NAME"
        }
    }.getOrNull()
}
