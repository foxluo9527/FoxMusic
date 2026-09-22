package com.foxluo.baselib.util

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

object ViewExt {
    val nowTime
        get() = System.currentTimeMillis()

    //扩展函数，view隐藏
    fun View.gone() {
        visibility = View.GONE
    }

    //扩展函数，view显示
    fun View.visible() {
        visibility = View.VISIBLE
    }

    fun View.inVisible(){
        visibility = View.INVISIBLE
    }

    fun View.visible(visible: Boolean) {
        visibility = if (visible) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    fun View.inVisible(inVisible: Boolean){
        visibility = if (inVisible) {
            View.INVISIBLE
        } else {
            View.VISIBLE
        }
    }

    /**
     * 防止连续点击
     */
    fun View.fastClick(clickDelay: Long = 1000L, callback: (View) -> Unit) {
        setOnClickListener {
            val currentTime = nowTime
            ((getTag(id) as? Long)?.let {
                if (it + clickDelay < nowTime) {
                    callback.invoke(this)
                    currentTime
                } else {
                    it
                }
            } ?: kotlin.run {
                callback.invoke(this)
                currentTime
            }).also {
                setTag(id, it)
            }
        }
    }
}

