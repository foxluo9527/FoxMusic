// :core:data-android —— :core:data 的 Android 实现层。
//
// 拆分原因与 :core:network-android 完全一致：KMP 模块（com.android.kotlin.multiplatform.library）
// 没有 Java 编译任务，而 Hilt/Dagger 生成的存根与工厂是 Java，放进 KMP 模块会被静默丢弃，
// 导致 :app 侧大面积 MissingBinding。因此凡带 @Module / @Provides / @Inject 的文件
// （Repository 实现、Worker、DownloadManager、DataModule …）都必须留在这个普通 Android 库里。
//
// namespace 刻意保持 "com.fox.music.core.data"（与拆分前一致），这样全项目对
// com.fox.music.core.data.* 的 import 一行都不用改；KMP 的 :core:data 改用
// "com.fox.music.core.data.shared"。
//
// 真正跨端的部分（实时消息协调器）在 :core:data 的 commonMain，iOS 端直接复用。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 跨端实时链路在 :core:data（KMP）。用 api，下游只依赖本模块即可拿到全部类型。
    api(project(":core:data"))

    implementation(project(":core:common"))
    implementation(project(":core:common-android"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    // :core:network 是跨端 DTO 层，Android 实现（ApiService / Retrofit / Hilt 装配）在
    // :core:network-android，后者 api 依赖前者，所以这里只依赖一个即可拿到全部类型。
    implementation(project(":core:network-android"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    // 跨端映射层（DTO ↔ 领域模型）
    implementation(project(":core:mapper"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.work)

    implementation(libs.ffmpeg.kit.min.gpl)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
