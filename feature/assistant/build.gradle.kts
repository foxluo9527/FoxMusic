import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :feature:assistant —— 客户端 AI 音乐助手的跨端部分（功能二·端侧 Increment 2）。
//
//   commonMain：AssistantContract（UiState/UiIntent/UiEffect + 对话 UI 模型）
//               AssistantContent（stateless 对话面板：消息气泡/动作芯片/
//               二次确认弹窗/输入栏/空态引导）
//   :feature:assistant-android：AssistantViewModel（@HiltViewModel + MVI 状态机）、
//               AssistantScreen 薄壳、AssistantRoutes
//   iosShared：后续 Increment 3 接 Ktor 版仓库 + Swift 壳复用同一份 Content
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
        namespace = "com.fox.music.feature.assistant.shared"
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
            // AssistantAction / AssistantRepository / DispatchResult 等契约类型，
            // api 让下游（-android 的 ViewModel）直接可见。
            api(project(":core:assistant"))

            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.materialIconsExtended)

            // 历史会话相对时间（今天/昨天/N天前）需要跨端日期计算
            implementation(libs.kotlinx.datetime)
        }
    }
}
