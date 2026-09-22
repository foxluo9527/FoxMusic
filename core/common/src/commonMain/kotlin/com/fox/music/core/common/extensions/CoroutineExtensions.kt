package com.fox.music.core.common.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import com.fox.music.core.common.result.Result

fun <T> Flow<T>.asResult(): Flow<Result<T>> = this
    .map<T, Result<T>> { Result.Success(it) }
    .onStart { emit(Result.Loading) }
    .catch { emit(Result.Error(it, it.message)) }

fun CoroutineScope.launchWithDelay(
    delayMillis: Long,
    block: suspend CoroutineScope.() -> Unit
): Job = launch {
    delay(delayMillis)
    block()
}

inline fun <T> Flow<T>.onError(crossinline action: suspend (Throwable) -> Unit): Flow<T> =
    catch { e ->
        action(e)
        throw e
    }
