package com.fox.music.crash

import android.app.Application
import android.os.Process
import com.fox.music.BuildConfig
import com.tencent.bugly.crashreport.CrashReport
import java.io.BufferedReader
import java.io.FileReader

object BuglyInitializer {

    fun init(application: Application) {
        val context = application.applicationContext
        val packageName = context.packageName
        val processName = currentProcessName()
        val isMainProcess = processName.isNullOrEmpty() || processName == packageName

        val strategy = CrashReport.UserStrategy(context).apply {
            setUploadProcess(isMainProcess)
            setAppVersion(BuildConfig.VERSION_NAME)
        }

        CrashReport.initCrashReport(
            context,
            BuildConfig.BUGLY_APP_ID,
            BuildConfig.DEBUG,
            strategy,
        )
    }

    private fun currentProcessName(): String? {
        return runCatching {
            BufferedReader(FileReader("/proc/${Process.myPid()}/cmdline")).use { reader ->
                reader.readLine()?.trim()?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }
}
