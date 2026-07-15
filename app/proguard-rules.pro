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