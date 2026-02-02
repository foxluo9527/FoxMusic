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
