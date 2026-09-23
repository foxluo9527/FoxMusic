import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :feature:player —— 播放器模块的跨端部分（KMP 骨架）。
//
//   commonMain：CustomLyricItem（纯 data class，依赖 core:model）
//   :feature:player-android：5 个 @HiltViewModel + 全部屏幕外壳（PlayerScreen /
//     SongPage / LyricPage 等，依赖 Media3 controller、LyricViewX 歌词渲染、
//     BackHandler / LocalContext / Toast、viewBinding）+ 歌词管理器 + 8 drawable + 2 layout
//   注：core:player 是 android.library（Media3/Hilt），不可进 commonMain；
//       歌词渲染 LyricViewX、桌面歌词均为 android-only，留 -android 侧；
//       iOS 播放器 UI 与歌词走 Swift 侧（AVPlayer + Live Activity，见批次 22-24）。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "com.fox.music.feature.player.shared"
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
            implementation(project(":core:ui"))
            implementation(project(":core:domain"))
            // core:player 是 android.library（非 KMP），不可进 commonMain，
            // 已在 :feature:player-android 侧依赖。

            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.materialIconsExtended)
        }
    }
}
