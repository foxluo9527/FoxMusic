package com.fox.music.feature.player.viewmodel

import androidx.lifecycle.ViewModel
import com.fox.music.core.network.token.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ManageViewModel @Inject constructor(
    val tokenManager: TokenManager
) : ViewModel() {

}