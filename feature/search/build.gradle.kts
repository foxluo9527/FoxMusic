import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :feature:search —— 搜索页的跨端部分。
//
//   commonMain：SearchContract（含 SearchResultTab / SearchMusicPlatform 与折叠阈值常量）
//               SearchRoutes（SEARCH_ROUTE 模板）
//               SearchContent + SearchResultsContent（单曲 / 歌手 / 专辑三个分页结果列表）
//   :feature:search-android：SearchViewModel、BackHandler、LocalFocusManager、
//                            searchRoute()（要 android.net.Uri 编码）、帖子结果列表
//                            （PostNoteGridCard 依赖仍在 androidMain 的视频组件）
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
        namespace = "com.fox.music.feature.search"
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
        }
    }
}
