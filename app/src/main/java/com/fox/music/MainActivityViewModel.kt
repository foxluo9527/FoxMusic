package com.fox.music

import androidx.lifecycle.ViewModel
import com.fox.music.core.player.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    val musicController: MusicController
) : ViewModel()
