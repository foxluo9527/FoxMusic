import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:network —— 跨端可复用的网络契约层：DTO、报文解析、公共鉴权路径。
//
// Android 侧的实现（Retrofit 接口、OkHttp 拦截器、Hilt 装配）在 :core:network-android。
// 之所以拆开：KMP 模块没有 Java 编译任务，而 Hilt/Dagger 生成的存根与工厂是 Java，
// 放进来会被静默丢弃并导致 :app 侧 MissingBinding。详见 :core:network-android/build.gradle.kts。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// 原先走 AGP 的 buildConfigField("String", "BASE_URL", ...)，但 BuildConfig 是
// Android 专有产物，KMP 的 commonMain 引用不到，因此改为生成普通 Kotlin 常量文件，
// 让 Android 与 iOS 都能拿到同一个后端地址。
val apiBaseUrl: String = providers.gradleProperty("FOX_MUSIC_BASE_URL")
    .orElse(providers.environmentVariable("FOX_MUSIC_BASE_URL"))
    .orElse("http://39.106.30.151:9000/")
    .get()

val generateFoxNetworkConfig = tasks.register("generateFoxNetworkConfig") {
    val outputDir = layout.buildDirectory.dir("generated/foxNetworkConfig/kotlin")
    inputs.property("apiBaseUrl", apiBaseUrl)
    outputs.dir(outputDir)
    doLast {
        val pkgDir = outputDir.get().asFile.resolve("com/fox/music/core/network")
        pkgDir.mkdirs()
        pkgDir.resolve("FoxNetworkConfig.kt").writeText(
            """
            |package com.fox.music.core.network
            |
            |/**
            | * 由 Gradle 任务 generateFoxNetworkConfig 生成，请勿手改。
            | * 用于替代 AGP 的 BuildConfig.BASE_URL —— 后者是 Android 专有产物，
            | * KMP 的 commonMain 引用不到。
            | */
            |object FoxNetworkConfig {
            |    const val API_BASE_URL: String = "$apiBaseUrl"
            |}
            |
            """.trimMargin()
        )
    }
}

kotlin {
    androidLibrary {
        namespace = "com.fox.music.core.network"
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
        commonMain {
            kotlin.srcDir(generateFoxNetworkConfig)
            dependencies {
                api(project(":core:common"))
                api(project(":core:model"))
                api(libs.kotlinx.serialization.json)
            }
        }

        // 序列化测试放 commonTest：只有 DTO 与 kotlinx.serialization，无平台依赖。
        // 用 kotlin("test") 而非 JUnit，这样将来加 iOS target 后同一批用例可直接在 iOS 上跑。
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
