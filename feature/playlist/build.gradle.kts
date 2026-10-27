import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :feature:playlist —— 歌单广场 / 推荐专辑的跨端部分。
//
//   commonMain：PlaylistListContract / AlbumListContract + 两个 Content（纯 Compose，含分页列表）
//   :feature:playlist-android：4 个 @HiltViewModel + 外壳 + 歌单编辑页
//     （PlaylistEditScreen / PlaylistImageCropper 依赖 android.net.Uri、
//      ActivityResultContracts、Bitmap，只能留在安卓侧）
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "com.fox.music.feature.playlist"
        compileSdk = 36
        minSdk = 26

        withHostTestBuilder { }.configure { }

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(project(":core:ui"))

            api(libs.paging.common)
            api(libs.paging.compose)

            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.materialIconsExtended)
            implementation(libs.compose.ui.backhandler)
        }
    }
}
