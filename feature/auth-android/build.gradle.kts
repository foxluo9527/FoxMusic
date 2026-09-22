// :feature:auth-android —— Android 侧的状态机与依赖注入壳。
//
// 与 :feature:auth 配对：后者放跨端 UI 与契约，这里放依赖 Hilt 的
// AuthViewModel 与 hiltViewModel() 外壳（KMP 模块 ksp 不生效，必须放安卓模块）。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.feature.auth.android"
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
    // 跨端契约与界面（用 api：app 模块要用到其中的 LOGIN_ROUTE 等常量）
    api(project(":feature:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.bundles.coroutines)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
