import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:assistant —— 客户端 AI 音乐助手的跨端契约层（功能二·端侧）。
//
//   commonMain：与服务端 /api/ai 四端点一一对应的 wire DTO（kotlinx.serialization）
//               AssistantRepository（会话/对话数据入口）
//               AssistantActionDispatcher（动作意图执行入口）+ DispatchResult + ActionTypes
//   :core:assistant-android：Repository 实现（Retrofit，接口在 network-android）、
//               Dispatcher 实现（play_music 等走 MusicController / UseCase）
//   iosShared：后续补 Ktor 版 Repository 实现 + Swift 壳
//
// 设计铁律（依《FoxMusic AI 接入方案》4.1）：服务端只返回「数据 + 动作意图」，
// 动作由端侧经 UseCase / MusicController dispatch 执行——账号维度与鉴权天然复用既有通道。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "com.fox.music.core.assistant"
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
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
