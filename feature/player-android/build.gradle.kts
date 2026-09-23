// :feature:player-android —— 播放器模块的安卓侧状态机与屏幕外壳。
//
// 与 :feature:player 配对：后者放跨端纯数据（CustomLyricItem），这里放
//   1) @HiltViewModel（PlayerViewModel / ManageViewModel / CustomLyricSelectorViewModel 等）
//   2) 屏幕外壳（PlayerScreen / SongPage / LyricPage 等，依赖 Media3 controller、
//      LyricViewX 歌词渲染、BackHandler / LocalContext / Toast、viewBinding）
//   3) 歌词管理器（LyricStyleManager / LyricSyncManager / DesktopLyricContainer，android view）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.feature.player"
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
        viewBinding = true
    }
}

dependencies {
    // 用 api：app 模块要用到本模块的屏幕与路由常量
    api(project(":feature:player"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:player"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // 歌词渲染 + 布局（android-only）
    implementation(libs.lyricviewx)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraintlayout)

    // Paging3
    implementation(libs.paging)
    implementation(libs.paging.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    // Coroutines
    implementation(libs.bundles.coroutines)

    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
