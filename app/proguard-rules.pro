# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line numbers for readable crash stack traces (Bugly / R8)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Bugly
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.** { *; }

# Widget
-keep class com.fox.music.widget.WidgetActionReceiver { *; }
-keep class com.fox.music.widget.FoxMusicWidgetSmallReceiver { *; }
-keep class com.fox.music.widget.FoxMusicWidgetReceiver { *; }
-keep class com.fox.music.widget.FoxMusicWidgetLargeReceiver { *; }
-keep interface com.fox.music.widget.WidgetMusicEntryPoint { *; }
-keep interface com.fox.music.widget.WidgetBootstrapEntryPoint { *; }

# Android Auto Car App
-keep class com.fox.music.feature.automotive.** { *; }
-keep interface com.fox.music.feature.automotive.CarEntryPoint { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep,includedescriptorclasses class com.fox.music.core.network.model.** { *; }
-keep,includedescriptorclasses class com.fox.music.core.model.** { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# FFmpegKit（音视频压缩：动态照片/视频发布、聊天发视频）
# 实测根因（R8 mapping 对照实验）：release 开 R8 后 com.arthenica.ffmpegkit.AbiDetect 的
# **native 方法声明被整体删掉**（mapping 里 getNativeAbi/getNativeCpuAbi/isNativeLTSBuild 全无），
# 类名虽保留，但 .so 的 JNI_OnLoad 按类名做 RegisterNatives（strings 可见 abiDetectClassName /
# abiDetectMethods）时找不到方法 → 注册失败 → 返回 0：
#   java.lang.Error: FFmpegKit failed to start ...
#   Caused by: UnsatisfiedLinkError: Bad JNI version returned from JNI_OnLoad ...: 0
# 这个 AAR 不自带 consumer proguard 规则（解包确认无 proguard.txt），所以必须手写。
# 该异常是 java.lang.Error 而非 Exception，业务侧 catch(Exception) 兜不住 → release 直接崩。
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }
-dontwarn com.arthenica.**

# 通用兜底：任何被 JNI 按名字/RegisterNatives 绑定的 native 方法都不能被改名或删除
# （AGP 的 proguard-android-optimize.txt 并不包含这一条，缺了就是上面那个 release-only 崩溃）。
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

