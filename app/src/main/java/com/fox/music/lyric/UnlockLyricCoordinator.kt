package com.fox.music.lyric

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.player.controller.MusicController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class UnlockLyricCoordinator @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicController: MusicController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var receiverRegistered = false
    private var lockScreenLyricEnabled = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    mainHandler.removeCallbacks(screenOnShowRunnable)
                    // 锁屏时只在后台准备歌词页，绝不能点亮屏幕（否则用户按电源键锁不了屏）
                    showLockScreenLyric(context)
                }
                Intent.ACTION_SCREEN_ON -> {
                    mainHandler.removeCallbacks(screenOnShowRunnable)
                    mainHandler.postDelayed(screenOnShowRunnable, SCREEN_ON_SHOW_DELAY_MS)
                }
                Intent.ACTION_USER_PRESENT -> showLockScreenLyric(context)
            }
        }
    }

    private val screenOnShowRunnable = Runnable {
        val context = appContext ?: return@Runnable
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        if (keyguardManager.isKeyguardLocked) {
            showLockScreenLyric(context)
        }
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            userPreferencesRepository.userPreferences
                .map { it.lockScreenLyricEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    lockScreenLyricEnabled = enabled
                    if (enabled) {
                        registerUnlockDetectionIfNeeded()
                    } else {
                        unregisterUnlockDetectionIfNeeded()
                    }
                }
        }
    }

    fun preview(context: Context) {
        if (!canShowLyric(ignoreEnabled = true)) return
        launchLockScreenLyric(context)
    }

    private fun showLockScreenLyric(context: Context) {
        if (!canShowLyric()) return
        launchLockScreenLyric(context)
    }

    private fun canShowLyric(ignoreEnabled: Boolean = false): Boolean {
        if (!lockScreenLyricEnabled && !ignoreEnabled) return false
        val state = musicController.playerState.value
        if (state.currentMusic == null) return false
        if (!state.isPlaying && state.position <= 0L) return false
        return true
    }

    private fun launchLockScreenLyric(context: Context) {
        val music = musicController.playerState.value.currentMusic ?: return
        val intent = LockScreenLyricActivity.intent(context).apply {
            putExtra(LockScreenLyricActivity.EXTRA_MUSIC_ID, music.id)
        }
        context.startActivity(intent)
    }

    private fun registerUnlockDetectionIfNeeded() {
        val context = appContext ?: return
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            context,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterUnlockDetectionIfNeeded() {
        val context = appContext ?: return
        mainHandler.removeCallbacks(screenOnShowRunnable)
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
    }

    companion object {
        private const val SCREEN_ON_SHOW_DELAY_MS = 300L
    }
}
