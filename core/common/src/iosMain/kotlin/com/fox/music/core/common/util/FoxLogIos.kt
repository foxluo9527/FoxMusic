package com.fox.music.core.common.util

/**
 * iOS 侧日志实现。
 *
 * ⚠️ 这里**不能用 `NSLog`** —— 已实测会崩：
 * `NSLog(format, vararg args)` 是 C 可变参数函数，把 Kotlin `String` 传进可变参数列表
 * 时**不会**桥接成 `NSString`，而是原样把 Kotlin 对象的指针交出去；CoreFoundation 在
 * 格式化 `%@` 时对那个指针调 `objc_opt_respondsToSelector`（判断是否实现
 * `description`）→ EXC_BAD_ACCESS / SIGSEGV。
 * 崩溃栈形如：NSLog → _CFStringCreateWithFormatAndArgumentsAux2 → objc_opt_respondsToSelector。
 *
 * 因此改用 `println`（与项目里 FoxIosBridge 等既有 iOS 代码同一约定）：
 * 输出到 stdout，Xcode 控制台与 `xcrun simctl launch --console` 都能看到。
 */
actual object FoxLog {
    actual fun d(tag: String, message: String) {
        println("[D/$tag] $message")
    }

    actual fun i(tag: String, message: String) {
        println("[I/$tag] $message")
    }

    actual fun w(tag: String, message: String) {
        println("[W/$tag] $message")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        val detail = throwable?.let { " | ${it.message}" } ?: ""
        println("[E/$tag] $message$detail")
    }
}
