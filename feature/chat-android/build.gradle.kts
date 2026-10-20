// :feature:chat-android —— 聊天模块的安卓侧状态机与屏幕外壳。
//
// 与 :feature:chat 配对：后者放跨端纯 Compose 组件与纯 Kotlin 工具，这里放
//   1) 12 个 @HiltViewModel
//   2) 屏幕外壳（MessagesScreen / ChatDetailScreen / FriendsScreen 等，
//      依赖 uCrop / 相机 / xxpermissions / ffmpeg-kit 视频压缩 / Media3 transformer /
//      VoiceRecorder / BackHandler / LocalContext / Toast 等 android-only 能力）
//   3) ChatRoutes（android.net.Uri 编码）、ChatScreen（引用 MessagesScreen）
//   4) ConversationItem（引用 core:data.mapper，android-only）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.feature.chat"
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
    // 用 api：app 模块要用到本模块的屏幕与路由常量
    api(project(":feature:chat"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:datastore"))
    implementation(project(":core:data-android"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.ucrop)
    implementation(libs.xxpermissions)
    implementation(libs.coil.video)
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.ffmpeg.kit.min.gpl)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.compose.foundation.layout)
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
