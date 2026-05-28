package com.fox.music.core.player.timer

import com.fox.music.core.model.music.SleepTimerState
import com.fox.music.core.player.controller.MusicController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepTimerManager @Inject constructor(
    private val musicController: MusicController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    fun start(durationMs: Long) {
        require(durationMs > 0) { "duration must be positive" }
        cancelTimer(resetState = false)
        val endTimeMs = System.currentTimeMillis() + durationMs
        _state.value = SleepTimerState(
            isActive = true,
            remainingMs = durationMs,
            totalMs = durationMs,
        )
        timerJob = scope.launch {
            while (isActive) {
                val remaining = endTimeMs - System.currentTimeMillis()
                if (remaining <= 0L) {
                    musicController.pause()
                    _finished.tryEmit(Unit)
                    cancelTimer(resetState = true)
                    break
                }
                _state.value = _state.value.copy(remainingMs = remaining)
                delay(TICK_MS)
            }
        }
    }

    fun cancel() {
        cancelTimer(resetState = true)
    }

    private fun cancelTimer(resetState: Boolean) {
        timerJob?.cancel()
        timerJob = null
        if (resetState) {
            _state.value = SleepTimerState()
        }
    }

    companion object {
        private const val TICK_MS = 1_000L
    }
}
