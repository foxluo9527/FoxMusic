// :core:assistant-android —— 客户端 AI 助手的 Android 实现层。
//
// 与 :core:assistant（KMP 契约）配对：
//   1) AssistantRepositoryImpl：Retrofit（AiAssistantApiService 在 network-android）→ 仓库接口
//   2) AssistantActionDispatcherImpl：动作意图 → MusicController / UseCase dispatch
//      （play_music 已接通；其余动作返回 Unsupported，由 UI 提示，待增量补齐）
//   3) Hilt 装配（本模块无 @HiltViewModel，放 core 层供多 feature 复用）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.core.assistant.android"
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
    // 契约与 wire DTO（KMP），api 让下游直接可见
    api(project(":core:assistant"))

    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:player"))
    // Retrofit 接口与实例由 network-android 的 NetworkModule 提供
    implementation(project(":core:network-android"))

    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
