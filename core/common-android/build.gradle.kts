// 本模块是 :core:common 的 **Android 侧装配层**。
//
// 只放带 Dagger/Hilt 注解的文件（@Module / @InstallIn / @Provides / @Binds / @Inject）。
// 原因见 :core:network-android 的注释：KMP 模块没有 Java 编译任务，
// 而 Hilt/Dagger 生成的存根与工厂都是 Java，放在 KMP 模块里会被静默丢弃，
// 导致 :app 侧 Hilt 聚合发现不到这些绑定。
//
// :core:common（KMP）里仍然保留纯 Kotlin 的 Android 工具类（ImageExt / Logger / MviViewModel 等），
// 它们不产生 Dagger 代码，可以正常编译进 AAR。只有需要生成 Java 的那几个文件搬到了这里。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.core.common.android"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:common"))
    implementation(libs.bundles.coroutines)
    implementation(libs.timber)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
