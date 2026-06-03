package com.fox.music.realtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import timber.log.Timber

/**
 * 登录后引导用户关闭电池优化、允许后台活动（一加 / ColorOS 等 ROM）。
 */
object BackgroundPermissionGuide {

    private const val PREFS_NAME = "fox_background_permission_guide"
    private const val KEY_USER_CONFIRMED_SETUP = "user_confirmed_background_setup"

    fun shouldShowAfterLogin(context: Context): Boolean {
        if (isUserConfirmed(context)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return true
    }

    fun isUserConfirmed(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USER_CONFIRMED_SETUP, false)
    }

    fun markUserConfirmed(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USER_CONFIRMED_SETUP, true)
            .apply()
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 打开系统设置：电池无限制 + 尝试跳转厂商「后台活动 / 自启动」页 */
    fun openBackgroundSettings(context: Context) {
        if (!isIgnoringBatteryOptimizations(context)) {
            requestIgnoreBatteryOptimizations(context)
            return
        }
        if (!tryOpenOemBackgroundSettings(context)) {
            openAppDetailsSettings(context)
        }
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) {
            tryOpenOemBackgroundSettings(context)
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.w(e, "无法打开电池优化白名单页")
            openAppDetailsSettings(context)
        }
    }

    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.w(e, "无法打开应用详情设置")
        }
    }

    private fun tryOpenOemBackgroundSettings(context: Context): Boolean {
        val packageName = context.packageName
        val candidates = listOf(
            // ColorOS / OxygenOS：自启动 / 后台活动
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                ),
            ),
            Intent().setComponent(
                ComponentName(
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.permission.startup.StartupAppListActivity",
                ),
            ),
            // 一加
            Intent().setComponent(
                ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
                ),
            ),
            // 小米
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            ),
            // 华为
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
            ),
            // 三星等：应用详情 → 电池
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            },
        )
        for (base in candidates) {
            val intent = base.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent.resolveActivity(context.packageManager) != null) {
                try {
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    Timber.w(e, "OEM 后台设置跳转失败: ${intent.component}")
                }
            }
        }
        return false
    }
}
