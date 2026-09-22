import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :feature:auth —— 登录 / 注册 / 重置的跨端部分。
//
// 批次 12 试点：确立 feature 层迁移的标准模式
//   commonMain：MVI 契约（AuthContract）+ 纯 Compose 界面（LoginContent）
//   :feature:auth-android：Hilt ViewModel 与 hiltViewModel() 外壳
// 原因是 KMP 模块没有 Java 编译任务，ksp 不生效，@HiltViewModel 的工厂生成不出来。
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
        namespace = "com.fox.music.feature.auth"
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

            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.materialIconsExtended)
        }
    }
}
