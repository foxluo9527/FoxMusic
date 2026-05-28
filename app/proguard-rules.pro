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