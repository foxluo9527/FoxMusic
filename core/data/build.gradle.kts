import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:data —— 跨端数据层（KMP）。
//
// 迁移背景：原先这一层同时装着「可跨端的实时消息协调逻辑」和「Android 专属的
// Room/WorkManager/Download 实现」，导致 iOS 端完全用不上。现在拆成：
//
//   :core:data         KMP          commonMain = 实时链路（RealtimeMessageCoordinator 等）
//   :core:data-android android.lib  src/main   = Repository 实现 / Worker / Hilt 装配（49 个文件）
//
// 包名一律保持 com.fox.music.core.data.*，因此上层 import 零改动，只需把 Gradle
// 依赖从 :core:data 换成 :core:data-android。
//
// 硬约束：本模块不能配 ksp/Hilt（KMP 模块无 Java 编译任务，配 ksp 会在 Configure 阶段直接
// FAILURE：The 'ksp' configuration is deprecated in Kotlin Multiplatform projects）。
// 需要注入的类改为普通构造器，由 :core:data-android 的 Hilt Module 手工装配。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        // 原 namespace 让给 :core:data-android，避免上层 import 变动
        namespace = "com.fox.music.core.data.shared"
        compileSdk = 36
        minSdk = 26

        withHostTestBuilder {}.configure {}

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            api(project(":core:model"))
            api(project(":core:domain"))
            // WebSocketManager 接口与 DTO 在 :core:network（KMP）；
            // Android 的 OkHttp 实现在 :core:network-android，iOS 用 Ktor Darwin。
            api(project(":core:network"))
            // DTO ↔ 领域模型映射（零平台依赖）
            api(project(":core:mapper"))
        }
    }
}
