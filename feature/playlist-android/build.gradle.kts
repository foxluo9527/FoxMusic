// :feature:playlist-android —— 歌单模块的安卓侧状态机、外壳与图片裁剪页。
//
// 与 :feature:playlist 配对：后者放跨端契约与界面，这里放
//   1) 4 个 @HiltViewModel（PlaylistList / AlbumList / PlaylistDetail / PlaylistEdit）
//   2) 对应的 hiltViewModel() 外壳
//   3) 歌单详情页（含 android.widget.Toast + BackHandler + LocalContext）
//   4) 歌单编辑页 + 图片裁剪（android.net.Uri / ActivityResultContracts / Bitmap / uCrop）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.feature.playlist.android"
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
    // 用 api：app 模块要用到 PLAYLIST_LIST_ROUTE / ALBUM_LIST_ROUTE / playlistDetailRoute
    api(project(":feature:playlist"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))

    implementation(libs.coil.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.paging)

    implementation(libs.bundles.coroutines)

    implementation(libs.androidx.activity.compose)
    implementation(libs.ucrop)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
