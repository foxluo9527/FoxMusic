package com.fox.music.core.common

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.MutableStateFlow

/**
 *    Author : 罗福林
 *    Date   : 2026/1/4
 *    Desc   : 全局事件管理
 */
object EventViewModel {

    val showMainPageRoute = MutableStateFlow<String?>(null)

    val appInForeground by lazy {
        MutableLiveData<Boolean>(true)
    }
}