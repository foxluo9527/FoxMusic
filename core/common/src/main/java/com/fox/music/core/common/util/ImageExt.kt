package com.foxluo.baselib.util

import android.net.Uri
import android.widget.ImageView
import com.blankj.utilcode.util.SizeUtils.dp2px
import com.bumptech.glide.Glide
import com.fox.music.core.common.R
import jp.wasabeef.glide.transformations.BlurTransformation
import jp.wasabeef.glide.transformations.CropCircleTransformation
import jp.wasabeef.glide.transformations.RoundedCornersTransformation

object ImageExt {
    fun ImageView.loadUri(uri: Uri?) {
        Glide.with(this)
            .load(uri)
            .placeholder(R.mipmap.ic_app)
            .error(R.mipmap.ic_app)
            .into(this)
    }

    fun ImageView.loadUrl(url: String?) {
        Glide.with(this)
            .load(processUrl(url)?:R.mipmap.ic_app)
            .placeholder(R.mipmap.ic_app)
            .error(R.mipmap.ic_app)
            .into(this)
    }
    fun ImageView.loadThumbnailUrl(url: String?,thumbnail: Float=0.7f) {
        Glide.with(this)
            .load(url?:R.mipmap.ic_app)
            .placeholder(R.mipmap.ic_app)
            .thumbnail(thumbnail)
            .error(R.mipmap.ic_app)
            .into(this)
    }


    fun ImageView.loadUrlWithCorner(url: String?, radius: Int) {
        Glide.with(this)
            .load(url?:R.mipmap.ic_app)
            .transform(RoundedCornersTransformation(dp2px(radius.toFloat()), 0))
            .placeholder(R.mipmap.ic_app)
            .error(R.mipmap.ic_app)
            .into(this)
    }

    fun ImageView.loadUrlWithCircle(url: String?) {
        Glide.with(this)
            .load(processUrl(url)?:R.mipmap.ic_app)
            .transform(CropCircleTransformation())
            .placeholder(R.mipmap.ic_app_round)
            .error(R.mipmap.ic_app_round)
            .into(this)
    }

    fun processUrl(url: String?): String? {
        return if (!(url.isNullOrEmpty()) && ! url.startsWith("http")) {
            "http://39.106.30.151:9000$url"
        } else {
            if (url.isNullOrEmpty()) {
                null
            } else {
                url
            }
        }
    }
}