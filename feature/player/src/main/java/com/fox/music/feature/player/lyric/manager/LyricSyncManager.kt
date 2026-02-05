package com.fox.music.feature.player.lyric.manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.lifecycle.asFlow
import androidx.media3.common.util.UnstableApi
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.SPUtils
import com.blankj.utilcode.util.ToastUtils
import com.fox.music.core.common.EventViewModel
import com.fox.music.core.model.PlayerState
import com.fox.music.feature.player.lyric.data.LyricStyle
import com.fox.music.feature.player.lyric.ui.DesktopLyricContainer
import com.fox.music.feature.player.ui.screen.PLAYER_ROUTE
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@UnstableApi
class LyricSyncManager private constructor() {
    companion object {
        private const val TAG = "LyricSyncManager"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: LyricSyncManager? = null

        fun getInstance(): LyricSyncManager {
            return instance ?: synchronized(this) {
                instance ?: LyricSyncManager().also {
                    instance = it
                }
            }
        }
    }

    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
        observePlayerState()
        windowManager = this.context.getSystemService(WINDOW_SERVICE) as WindowManager
        setupLyricView()
    }

    private lateinit var windowManager: WindowManager
    private lateinit var lyricView: DesktopLyricContainer

    @Volatile
    private var isShowing = false
    private val playManager by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            MusicControllerEntryPoint::class.java
        ).getMusicController()
    }

    private val styleManager by lazy {LyricStyleManager.getInstance()}

    private var currentMusicId: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val hideRunnable = Runnable {
        lyricView.setBackgroundVisible(false)
    }
    private val _isDesktopLyricEnabled =
        MutableStateFlow(SPUtils.getInstance().getBoolean("is_desktop_enable"))
    val isDesktopLyricEnabled: StateFlow<Boolean> = _isDesktopLyricEnabled.asStateFlow()

    val isDesktopLyricLocked by lazy {
        MutableStateFlow<Boolean>(false)
    }

    private fun observePlayerState() {
        CoroutineScope(Dispatchers.Main).launch {
            launch {
                styleManager.styleFlow.collect {style ->
                    updateLyricStyle(style)
                }
            }
            launch {
                playManager.playerState.collectLatest {state ->
                    handlePlayerState(state)
                }
            }
            launch {
                isDesktopLyricEnabled.collectLatest {
                    SPUtils.getInstance().put("is_desktop_enable", it)
                }
            }
            launch {
                EventViewModel.appInForeground.asFlow().collect {
                    if (it && isShowing) {
                        hideWindow()
                    } else if (isDesktopLyricEnabled.value) {
                        showWindow()
                    }
                }
            }
        }
    }

    private fun handlePlayerState(state: PlayerState) {
        if (! _isDesktopLyricEnabled.value) return

        val currentMusic = state.currentMusic
        if (currentMusic == null) {
            hideWindow()
            return
        }

        val musicId = currentMusic.id.toString()
        val lyrics = currentMusic.lyrics
        val lyricsTrans = currentMusic.lyricsTrans

        // 实时更新歌词，无论音乐是否变化
        showLyric(
            musicId = musicId,
            lyricContent = lyrics,
            lyricTrans = lyricsTrans,
            progress = state.position
        )
        lyricView.setPlaying(state.isPlaying)
        lyricView.setPlaySong(currentMusic.coverImage, currentMusic.title)
    }

    fun openDesktopLyric(context: Context) {
        XXPermissions.with(context)
            .permission(Permission.SYSTEM_ALERT_WINDOW)
            .request(object: OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, all: Boolean) {
                    if (all) {
                        toggleDesktopLyric()
                    } else {
                        showPermissionDeniedHint()
                    }
                }

                override fun onDenied(permissions: MutableList<String>, never: Boolean) {
                    if (never) {
                        XXPermissions.startPermissionActivity(ActivityUtils.getTopActivity())
                    } else {
                        showPermissionDeniedHint()
                    }
                }
            })
    }

    private fun showPermissionDeniedHint() {
        ToastUtils.make().show("需要悬浮窗权限才能显示桌面歌词")
    }

//    fun showLyricStyleDialog() {
//        LyricStyleDialog.create(requireContext(), lyricStyleManager)
//            .setOnStyleChangedListener { style: LyricStyle ->
//                lyricSyncManager.updateLyricStyle(style)
//            }
//            .show()
//    }

    private fun setupLyricView() {
        lyricView = DesktopLyricContainer(context).apply {
            setOnLyricActionListener(object: DesktopLyricContainer.OnLyricActionListener {
                override fun onSingleTap() {
                    // 切换背景高亮和操作按钮显示/隐藏
                    resetHideTimer()
                    lyricView.setBackgroundVisible(lyricView.isActive.not())
                }

                override fun onSettingTap() {
                    resetHideTimer()
                }

                override fun onAppTap() {
                    val intent =
                        context.packageManager.getLaunchIntentForPackage(context.packageName)
                            ?.apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra("start_route", PLAYER_ROUTE)
                            }
                    context.startActivity(intent)
                }

                override fun onLock(isLocked: Boolean) {
                    if (isLocked) {
                        lyricView.setBackgroundVisible(false)
                    } else {
                        resetHideTimer()
                    }
                    isDesktopLyricLocked.value = isLocked
                    lyricView.setDraggable(! isLocked)
                    ToastUtils.make().show(if (isLocked) "已锁定,请在播放页解锁" else "已解锁")
                }

                override fun onExit() {
                    toggleDesktopLyric()
                }

                override fun onPrev() {
                    playManager.previous()
                    resetHideTimer()
                }

                override fun onPlayPause() {
                    playManager.togglePlay()
                    resetHideTimer()
                }

                override fun onNext() {
                    playManager.next()
                    resetHideTimer()
                }

                override fun onFavorite() {
                    // 收藏功能实现（需要根据实际情况调整）
                    val currentMusic = playManager.playerState.value?.currentMusic
                    if (currentMusic != null) {

                    }
                    resetHideTimer()
                }
            })
        }
        lyricView.setDraggable(true)
        lyricView.setAutoAttach(true)
        lyricView.setDragPadding(0, 0, 0, 0)
    }

    fun showLyric(
        musicId: String?,
        lyricContent: String?,
        lyricTrans: String?,
        progress: Long,
    ) {
        if (EventViewModel.appInForeground.value == true) return
        if (musicId != currentMusicId || ! isShowing) {
            currentMusicId = musicId
            lyricView.getLyricContentView().let {lyricView ->
                if (lyricTrans.isNullOrEmpty()) {
                    lyricView.loadLyric(lyricContent)
                } else {
                    lyricView.loadLyric(lyricContent, lyricTrans)
                }
            }
        } else {
            lyricView.getLyricContentView().updateTime(progress, true)
        }
        if (! isShowing) {
            showWindow()
        }
    }

    private fun showWindow() {
        if (EventViewModel.appInForeground.value == true) return
        addWindowView()
        resetHideTimer()
    }

    private fun addWindowView() {
        if (lyricView.parent != null) {
            isShowing = true
            return
        }
        lyricView.let {view ->
            val width = WindowManager.LayoutParams.MATCH_PARENT
            val height = WindowManager.LayoutParams.WRAP_CONTENT

            val params = WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSPARENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            windowManager.addView(view, params)
            lyricView.setupWindowManager(windowManager, params)
            isShowing = true
        }
    }

    private fun hideWindow() {
        if (! isShowing) return
        // 销毁歌词视图
        lyricView.parent?.let {
            windowManager.removeView(lyricView)
        }
        isShowing = false
    }

    private fun resetHideTimer() {
        mainHandler.removeCallbacks(hideRunnable)
        mainHandler.postDelayed(hideRunnable, 10000)
    }

    fun updateLyricStyle(style: LyricStyle) {
        lyricView.updateStyle(style)
    }

    fun enableDesktopLyric() {
        _isDesktopLyricEnabled.value = true
        ToastUtils.make().show("开启桌面歌词")
    }

    fun disableDesktopLyric() {
        _isDesktopLyricEnabled.value = false
        hideWindow()
        ToastUtils.make().show("关闭桌面歌词")
    }

    fun toggleDesktopLyric() {
        if (_isDesktopLyricEnabled.value) {
            disableDesktopLyric()
        } else {
            enableDesktopLyric()
        }
    }

    fun toggleLock() {
        lyricView.toggleLock()
    }

    fun getCurrentStyle(): LyricStyle {
        return styleManager.currentStyle
    }

}
