import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :feature:discover —— 发现 / 歌手列表 / 歌手详情的跨端部分。
//
// 沿用批次 12 确立的 feature 层迁移标准模式：
//   commonMain：MVI 契约（*Contract）+ 路由常量 + 纯 Compose 界面（*Content）
//   :feature:discover-android：4 个 Hilt ViewModel + hiltViewModel() 外壳 + Toast
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform)
    // CMP 1.6.10+ 要求同时应用 Kotlin 官方的 compose 编译器插件
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "com.fox.music.feature.discover"
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

            // 热门歌手页的分页列表（批次 14 后 paging-common / paging-compose 已在 commonMain）
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
