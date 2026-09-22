// :feature:home-android —— 首页的 Android 侧状态机与依赖注入壳。
//
// 与 :feature:home 配对：后者放跨端契约与界面（含分页列表），
// 这里只放 @HiltViewModel 的 HomeViewModel 与 hiltViewModel() 外壳。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.feature.home.android"
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
    // 用 api：app 模块要用到 HOME_ROUTE
    api(project(":feature:home"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.paging)

    implementation(libs.bundles.coroutines)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
