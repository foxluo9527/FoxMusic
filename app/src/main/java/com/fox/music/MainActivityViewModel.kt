package com.fox.music

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.music.core.network.token.TokenManager
import com.fox.music.core.player.controller.MusicController
import com.fox.music.feature.home.HOME_ROUTE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    val musicController: MusicController,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState = _authState.asStateFlow()

    
    init {
        observeAuthState()
    }
    
    private fun observeAuthState() {
        tokenManager.isLoggedIn
            .onEach {isLoggedIn ->
                _authState.update {it.copy(isLoggedIn = isLoggedIn) }
            }
            .launchIn(viewModelScope)
    }
    
    data class AuthState(
        val isLoggedIn: Boolean = true
    )
}
