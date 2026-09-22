// :feature:search-android —— 搜索页的 Android 侧状态机与平台能力壳。
//
// 与 :feature:search 配对：后者放跨端契约与界面，这里放
//   1) @HiltViewModel 的 SearchViewModel
//   2) BackHandler（androidx.activity）+ LocalFocusManager（软键盘）
//   3) searchRoute() 的 Uri 编码
//   4) 帖子结果列表（PostNoteGridCard → PostVideoCover 仍在 androidMain）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.feature.search.android"
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
    // 用 api：app 模块要用到 SEARCH_ROUTE 与 searchRoute()
    api(project(":feature:search"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.activity.compose)

    implementation(libs.paging)

    implementation(libs.bundles.coroutines)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
