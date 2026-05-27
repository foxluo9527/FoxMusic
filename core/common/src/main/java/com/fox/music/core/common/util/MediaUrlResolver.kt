package com.fox.music.core.common.util

import com.fox.music.core.common.constants.AppConstants

object MediaUrlResolver {
    fun resolve(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        return if (url.startsWith("http")) url else "${AppConstants.Network.MEDIA_BASE_URL}$url"
    }
}
