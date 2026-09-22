// :feature:discover-android —— Android 侧的状态机、依赖注入壳与 Paging 页面。
//
// 与 :feature:discover 配对：后者放跨端契约与界面，这里放
//   1) 4 个 @HiltViewModel（Discover / ArtistList / ArtistDetail / HotArtistList）
//   2) 对应的 hiltViewModel() 外壳 + Toast（LocalContext 是 Android 专属）
//   3) HotArtistListScreen —— androidx.paging 目前只有 Android 变体，暂不进 commonMain
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.feature.discover.android"
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
    // 跨端契约与界面（用 api：app 模块要用到 DISCOVER_ROUTE / artistDetailRoute 等）
    api(project(":feature:discover"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // 热门歌手页的分页（androidx.paging 暂无 KMP 变体）
    implementation(libs.paging)
    implementation(libs.paging.compose)

    implementation(libs.bundles.coroutines)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
