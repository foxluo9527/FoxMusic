package com.fox.music.crash

import android.app.Application
import com.fox.music.BuildConfig
import com.tencent.bugly.crashreport.CrashReport
import timber.log.Timber

object BuglyInitializer {

    private const val TAG = "Bugly"
    /** Bugly 后台注册包名（与 release 的 applicationId 一致，勿含 .debug 后缀） */
    private const val BUGLY_PACKAGE_NAME = "com.fox.music"

    fun init(application: Application) {
        val context = application.applicationContext

        val appVersion = if (BuildConfig.DEBUG) {
            "${BuildConfig.VERSION_NAME}-debug"
        } else {
            BuildConfig.VERSION_NAME
        }
        val channel = if (BuildConfig.DEBUG) "debug" else "release"

        val strategy = CrashReport.UserStrategy(context).apply {
            isUploadProcess = true
            appPackageName = BUGLY_PACKAGE_NAME
            setAppVersion(appVersion)
            appChannel = channel
        }

        CrashReport.initCrashReport(
            context,
            BuildConfig.BUGLY_APP_ID,
            BuildConfig.DEBUG,
            strategy,
        )
        CrashReport.setIsDevelopmentDevice(context, BuildConfig.DEBUG)
        Timber.tag(TAG)
            .i("initialized: package=$BUGLY_PACKAGE_NAME, version=$appVersion, channel=$channel")
    }
}
