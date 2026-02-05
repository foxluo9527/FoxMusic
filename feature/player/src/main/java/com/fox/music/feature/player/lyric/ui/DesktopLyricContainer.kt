package com.fox.music.feature.player.lyric.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.blankj.utilcode.util.ConvertUtils
import com.blankj.utilcode.util.ToastUtils
import com.dirror.lyricviewx.LyricViewX
import com.fox.music.feature.player.databinding.LayoutDesktopLyricBinding
import com.fox.music.feature.player.databinding.LayoutLyricSettingsBinding
import com.fox.music.feature.player.lyric.data.LyricStyle
import com.foxluo.baselib.util.ImageExt.loadUrlWithCircle
import com.foxluo.baselib.util.ViewExt.gone
import com.foxluo.baselib.util.ViewExt.inVisible
import com.foxluo.baselib.util.ViewExt.visible
import com.fox.music.feature.player.lyric.manager.LyricStyleManager
import com.fox.music.feature.player.lyric.ui.LyricSettingsExt.setListener

@SuppressLint("ClickableViewAccessibility")
class DesktopLyricContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DesktopDragView(context, attrs, defStyleAttr) {

    interface OnLyricActionListener {
        fun onSingleTap()
        fun onSettingTap()
        fun onAppTap()
        fun onLock(isLocked: Boolean)
        fun onExit()
        fun onPrev()
        fun onPlayPause()
        fun onNext()
        fun onFavorite()
    }

    // 歌词内容视图
    private lateinit var binding: LayoutDesktopLyricBinding

    // 锁定状态
    private var isLocked = false

    // 背景可见性状态
    var isActive = true

    // 播放状态
    private var isPlaying = false

    // 收藏状态
    private var isFavorite = false

    // 监听者
    private var listener: OnLyricActionListener? = null

    // 拖动相关变量
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val styleManager by lazy { LyricStyleManager.getInstance() }

    init {
        initView()
        setupListeners()
        setAutoAttach(false)
        setDraggable(true)
        setOnClickListener {
            listener?.onSingleTap()
        }
    }

    /**
     * 初始化视图
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initView() {
        // 使用LayoutInflater加载布局
        val inflater = LayoutInflater.from(context)
        inflater.inflate(com.fox.music.feature.player.R.layout.layout_desktop_lyric, this, true)
        setBackgroundColor(Color.TRANSPARENT)
        // 初始化歌词内容视图
        binding = LayoutDesktopLyricBinding.bind(this.getChildAt(0))
        binding.lyricContentView.setLabel("暂无歌词")
        binding.lyricContentView.setDraggable(false, null)
        binding.lyricContentView.setOnTouchListener { v: View?, event: MotionEvent? ->
            return@setOnTouchListener this.onTouchEvent(event)
        }
        // 设置初始状态
        updateLockButton()
        updatePlayPauseButton()
        updateFavoriteButton()
        updateVisibility()
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 顶部按钮监听器
        binding.btnLock.setOnClickListener {
            toggleLock()
        }
        binding.btnExit.setOnClickListener {
            if (isLocked) {
                ToastUtils.make().show("请先解锁")
                return@setOnClickListener
            }
            listener?.onExit()
        }
        binding.btnPrev.setOnClickListener {
            if (isLocked) {
                ToastUtils.make().show("请先解锁")
                return@setOnClickListener
            }
            listener?.onPrev()
        }
        binding.btnPlayPause.setOnClickListener {
            if (isLocked) {
                ToastUtils.make().show("请先解锁")
                return@setOnClickListener
            }
            listener?.onPlayPause()
        }
        binding.btnNext.setOnClickListener {
            if (isLocked) {
                ToastUtils.make().show("请先解锁")
                return@setOnClickListener
            }
            listener?.onNext()
        }
        binding.btnSettings.setOnClickListener {
            if (isLocked) {
                ToastUtils.make().show("请先解锁")
                return@setOnClickListener
            }
            binding.settings.visible(!binding.settings.isVisible)
        }
        binding.btnFavorite.setOnClickListener {
            if (isLocked) {
                ToastUtils.make().show("请先解锁")
                return@setOnClickListener
            }
            toggleFavorite()
        }
        val layoutSettings = LayoutLyricSettingsBinding.bind(binding.root)
        layoutSettings.setListener(styleManager)
        binding.llTop.getChildAt(1).setOnClickListener {
            listener?.onAppTap()
        }
    }

    /**
     * 切换锁定状态
     */
    fun toggleLock() {
        isLocked = !isLocked
        updateLockButton()
        listener?.onLock(isLocked)
        setTouchAble(!isLocked)
    }

    /**
     * 更新锁定按钮图标
     */
    private fun updateLockButton() {
        val drawableId = if (isLocked) {
            com.fox.music.core.common.R.drawable.ic_lock
        } else {
            com.fox.music.core.common.R.drawable.ic_unlock
        }
        binding.btnLock.setImageDrawable(ContextCompat.getDrawable(context, drawableId))
    }

    /**
     * 设置播放状态
     */
    fun setPlaying(playing: Boolean) {
        isPlaying = playing
        updatePlayPauseButton()
    }

    fun setPlaySong(icon: String?, title: String?) {
        binding.title.text = title ?: context.getString(com.fox.music.core.common.R.string.app_name)
        binding.icon.loadUrlWithCircle(icon)
    }

    /**
     * 更新播放/暂停按钮图标
     */
    private fun updatePlayPauseButton() {
        val drawableId = if (isPlaying) {
            com.fox.music.core.common.R.drawable.iv_pause
        } else {
            com.fox.music.core.common.R.drawable.iv_play
        }
        binding.btnPlayPause.setImageDrawable(ContextCompat.getDrawable(context, drawableId))
    }

    /**
     * 设置收藏状态
     */
    fun setFavorite(favorite: Boolean) {
        isFavorite = favorite
        updateFavoriteButton()
    }

    /**
     * 切换收藏状态
     */
    private fun toggleFavorite() {
        isFavorite = !isFavorite
        updateFavoriteButton()
        listener?.onFavorite()
    }

    /**
     * 更新收藏按钮图标
     */
    private fun updateFavoriteButton() {
        val drawableId = if (isFavorite) {
            com.fox.music.core.common.R.drawable.ic_favorite
        } else {
            com.fox.music.core.common.R.drawable.ic_favorite_border
        }
        binding.btnFavorite.setImageDrawable(ContextCompat.getDrawable(context, drawableId))
    }

    /**
     * 设置背景可见性
     */
    fun setBackgroundVisible(visible: Boolean) {
        isActive = visible
        updateVisibility()
    }

    /**
     * 更新控件可见性
     */
    private fun updateVisibility() {
        binding.settings.gone()
        binding.llTop.inVisible(!isActive)
        binding.llBottom.inVisible(!isActive)
        binding.back.inVisible(!isActive)
    }

    /**
     * 设置监听器
     */
    fun setOnLyricActionListener(l: OnLyricActionListener) {
        listener = l
    }

    /**
     * 更新歌词样式
     */
    fun updateStyle(style: LyricStyle) {
        binding.lyricContentView.setCurrentTextSize(
            ConvertUtils.sp2px(style.fontSize.toFloat()).toFloat()
        )
        binding.lyricContentView.setNormalTextSize(
            ConvertUtils.sp2px(style.fontSize.toFloat()).toFloat()
        )
        binding.lyricContentView.setCurrentColor(style.highlightColor)
        binding.lyricContentView.setNormalColor(style.textColor)
    }

    /**
     * 获取歌词内容视图
     */
    fun getLyricContentView(): LyricViewX = binding.lyricContentView
}
