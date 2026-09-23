// :iosShared —— iOS 宿主（iosApp/ 的 SwiftUI 外壳）消费的共享框架。
//
// 只做 iOS target：产出 FoxShared.framework，export 已跨端的 core 模块，
// 让 Swift 能直接看到 MusicDto 等类型。Android 端完全不依赖本模块，零影响。
//
// 网络传输用 Ktor（Darwin 引擎）：Android 的 Retrofit/OkHttp 是 JVM-only，
// 而 DTO（core:network commonMain 的 @Serializable 类）两端共用 ——
// 这正是计划里"播放相关接口走 Ktor"的最小落地。后续 UI 迁移阶段再考虑
// 把 Ktor service 层下沉到 :core:network commonMain 统一两端传输。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // Compose Multiplatform：iOS 端共享 UI 的可行性验证（批次 8）
    // CMP 1.6.10+ 要求同时应用 Kotlin 官方的 compose 编译器插件
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    @Suppress("UNCHECKED_CAST")
    targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java)
        .configureEach {
            binaries.framework {
                baseName = "FoxShared"
                isStatic = true
                // 导出传递依赖，否则 Swift 看不到 core 模块里的类型
                export(project(":core:network"))
                export(project(":core:common"))
                export(project(":core:model"))
            }
        }

    sourceSets {
        iosMain.dependencies {
            // export 到框架的依赖必须声明为 api（否则链接器拒绝导出）
            api(project(":core:network"))
            api(project(":core:common"))
            api(project(":core:model"))
            // 跨端设计系统层（批次 9 已 KMP 化）：两端共用同一套 Compose 组件
            api(project(":core:ui"))
            // 跨端映射层：两端共用同一份 DtoMappers
            api(project(":core:mapper"))
            // 已跨端的 feature 模块（commonMain Content 组件直接复用）：
            // 签名不对外暴露 feature 类型，故不需 export 到 Swift
            api(project(":feature:playlist"))
            // Compose Multiplatform（iOS 侧 UI，与 Android 的 androidx Compose 无关）
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            // Paging KMP：iOS 侧 PagingSource / Pager 直接可用（paging-compose 经 feature:playlist 传递）
            implementation(libs.paging.common)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.darwin)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}
