import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:ui —— 跨端设计系统层（组件、主题、设计令牌）。
//
// 迁移说明（批次 9）：本模块从 com.android.library 改为 KMP，源码拆成
//   - commonMain：纯 Compose 组件（包名仍是 androidx.compose.*，CMP 与 Android 完全一致）
//   - androidMain：Coil / Palette / Lottie / MediaPlayer / R 资源等平台能力
//   - iosMain：用 Ktor + Skia 实现的等价图片加载
// Android 端消费的是 commonMain + androidMain，与迁移前行为一致。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform)
    // CMP 1.6.10+ 要求同时应用 Kotlin 官方的 compose 编译器插件
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    // iOS 真机 + Apple Silicon 模拟器（iosMain 共享代码放 src/iosMain/kotlin）
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "com.fox.music.core.ui"
        compileSdk = 36
        minSdk = 26

        // AGP 9 的 KMP 模块里 Android 资源是 opt-in：不显式开启就生成不出 R 类
        androidResources { enable = true }

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

            // Compose Multiplatform 的 KMP 变体，包名与 Android 端一致
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.materialIconsExtended)
            api(compose.animation)
            // @Preview 注解：ui-tooling-preview 是 KMP 可用的；
            // 注意 ui-tooling 本身只有 Android 变体，放 commonMain 会导致
            // "KMP Dependencies Resolution Failure"，只能放 androidMain。
            implementation(compose.preview)
            implementation(libs.kotlinx.coroutines.core)
            // 跨端时间：替代 java.time（JVM-only），帖子时间格式化用它
            implementation(libs.kotlinx.datetime)
        }

        androidMain.dependencies {
            // Android 专属：图片加载 / 取色 / 动效 / 生命周期
            implementation(libs.coil.compose)
            implementation(libs.androidx.palette.ktx)
            implementation(libs.lottie.compose)
            implementation(libs.bundles.lifecycle)
            // @EntryPoint / @InstallIn 声明（KMP 模块没有 kapt/ksp，但这里只需要注解本体）
            implementation(libs.hilt.android)
            // Android Studio 预览运行时（仅 Android 变体）
            implementation(compose.uiTooling)
        }

        iosMain.dependencies {
            // iOS 侧图片加载走 Ktor(Darwin)，与 :iosShared 同一套引擎
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
