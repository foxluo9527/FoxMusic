package com.fox.music.iosshared

import com.fox.music.core.common.util.FoxLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * iOS 桥接层统一的协程异常兜底。
 *
 * ## 为什么必须有它
 *
 * Kotlin/Native 上协程里未捕获异常的默认终局是
 * `terminateWithUnhandledException` → `abort()`，**整个 App 直接卡死/闪退**
 * （JVM/Android 上未捕获异常也只崩当前线程，且有 Thread.UncaughtExceptionHandler
 * 可以上报；K/N 没有这层缓冲）。
 *
 * iOS 桥接层所有 `object` 级长生命周期作用域一律走 [foxMainScope] ——
 * 业务错误仍应在调用点 try-catch / runCatching，这里只拦截「漏网之鱼」
 * （抛了非预期的 Throwable、Swift 回调里再抛出的异常等），保证单次操作失败
 * 不会杀死进程。
 *
 * - [SupervisorJob]：子协程失败不连坐兄弟（页面里多个并发请求互不影响）。
 * - handler 只记录不重抛：异常到这里已经是「未捕获」终态，再抛就是 abort。
 */
private val foxCoroutineExceptionHandler = CoroutineExceptionHandler { context, throwable ->
    // CancellationException 理论上不会进到 handler（协程取消走独立路径），
    // 双保险：即便进来了也不当崩溃打日志。
    if (throwable is CancellationException) return@CoroutineExceptionHandler
    FoxLog.e(
        TAG,
        "协程未捕获异常已兜底拦截（未杀死进程）job=${context[Job]}: " +
            "${throwable::class.simpleName}: ${throwable.message}",
        throwable,
    )
}

/** 主线程安全作用域：UI 回调 / 状态更新 / 桥接层默认用它。 */
fun foxMainScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Main + foxCoroutineExceptionHandler)

/** 后台安全作用域：网络保活 / 长时 IO 用它。 */
fun foxBackgroundScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default + foxCoroutineExceptionHandler)

/**
 * 包一层 try-catch 的 launch：用于**长生命周期 collect**等不方便被异常打断的协程。
 *
 * 与仅靠 [CoroutineExceptionHandler] 的区别：handler 在协程已经因异常结束后才触发，
 * 无法让「while/collect 长循环」活下来；[safeLaunch] 在协程体内部捕获，
 * 配合 `retry`/重启逻辑可以让长流在单次异常后继续。[CancellationException]
 * 必须原样重抛，否则协程取消语义会被破坏。
 */
inline fun CoroutineScope.safeLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend CoroutineScope.() -> Unit,
): Job = launch(context) {
    try {
        block()
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        FoxLog.e(
            TAG,
            "safeLaunch 捕获异常: ${t::class.simpleName}: ${t.message}",
            t,
        )
    }
}

@PublishedApi
internal const val TAG = "FoxCoroutine"
