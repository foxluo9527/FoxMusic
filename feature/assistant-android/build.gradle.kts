// :feature:assistant-android —— AI 助手模块的安卓侧状态机与屏幕外壳。
//
// 与 :feature:assistant 配对：后者放跨端契约（AssistantContract）与
// stateless 对话面板（AssistantContent），这里放：
//   1) AssistantViewModel（@HiltViewModel + MVI 状态机）
//   2) AssistantScreen 薄壳（hiltViewModel + effect → Toast）
//   3) AssistantRoutes（路由常量）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.feature.assistant"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // 用 api：app 模块要用到本模块的 AssistantScreen 与 ASSISTANT_ROUTE
    api(project(":feature:assistant"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    // 仓库/调度器接口在 :core:assistant，Hilt 实现在 :core:assistant-android
    implementation(project(":core:assistant"))
    implementation(project(":core:assistant-android"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    // Coroutines
    implementation(libs.bundles.coroutines)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
