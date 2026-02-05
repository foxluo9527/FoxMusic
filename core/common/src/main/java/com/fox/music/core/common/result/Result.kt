package com.fox.music.core.common.result

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data
    fun exceptionOrNull(): Throwable? = (this as? Error)?.exception

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> Loading
    }

    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
        is Loading -> Loading
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (Throwable, String?) -> Unit): Result<T> {
        if (this is Error) action(exception, message)
        return this
    }

    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) action()
        return this
    }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(exception: Throwable, message: String? = null): Result<Nothing> = Error(exception, message)
        fun loading(): Result<Nothing> = Loading
    }
}

inline fun <T> runCatching(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    Result.Error(e, e.message)
}

suspend inline fun <T> suspendRunCatching(crossinline block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    Result.Error(e, e.message)
}

/**
 * Enhanced version that accepts a custom error message extractor
 */
suspend inline fun <T> suspendRunCatchingWithParser(
    crossinline errorParser: (Throwable) -> String,
    crossinline block: suspend () -> T
): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    Result.Error(e, errorParser(e))
}
