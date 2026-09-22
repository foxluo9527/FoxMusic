package com.fox.music.core.common.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface UiState
interface UiIntent
interface UiEffect

/**
 * 仅用于单页数据获取，多页数据请使用paging
 */
data class LoadState<T>(
    var page: Int = 1,
    val limit: Int = 20,
    val data: T? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
) {
    fun success(data: T?): LoadState<T> {
        return this.copy(
            data = data,
            isLoading = false,
            error = null,
            isSuccess = true
        )
    }

    fun error(error: String?): LoadState<T> {
        return this.copy(
            data = null,
            isLoading = false,
            error = error,
            isSuccess = false
        )
    }

    fun onLoading() = this.copy(data = null, isLoading = true, error = null, isSuccess = false)
}

abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _intent = MutableSharedFlow<I>()

    private val _effect = Channel<E>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    protected val currentState: S get() = _uiState.value

    init {
        viewModelScope.launch {
            _intent.collect { intent ->
                handleIntent(intent)
            }
        }
    }

    fun sendIntent(intent: I) {
        viewModelScope.launch {
            _intent.emit(intent)
        }
    }

    protected fun updateState(reducer: S.() -> S) {
        _uiState.value = currentState.reducer()
    }

    protected fun sendEffect(effect: E) {
        viewModelScope.launch {
            _effect.send(effect)
        }
    }

    protected abstract fun handleIntent(intent: I)
}
