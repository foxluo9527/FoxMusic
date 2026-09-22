import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 下 com.android.library 不能与 KMP 插件共存，改用 KMP 专用的 Android 库插件
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform)
    // 这里不能用 KSP/Hilt：KMP 模块没有 Java 编译任务，而 Hilt/Dagger 生成的
    // hilt_aggregated_deps 存根与 *Factory.java 都是 Java，放进来会被静默丢弃。
    // 带 Dagger 注解的文件因此挪到了 :core:common-android。
}

// 原先走 AGP 的 buildConfigField，但 BuildConfig 是 Android 专有产物，
// KMP 的 commonMain 引用不到，因此改为生成一份普通 Kotlin 常量文件。
val mediaBaseUrl: String = providers.gradleProperty("FOX_MUSIC_MEDIA_BASE_URL")
    .orElse(providers.environmentVariable("FOX_MUSIC_MEDIA_BASE_URL"))
    .orElse("http://39.106.30.151:9000")
    .get()

val generateFoxBuildConfig = tasks.register("generateFoxBuildConfig") {
    val outputDir = layout.buildDirectory.dir("generated/foxBuildConfig/kotlin")
    inputs.property("mediaBaseUrl", mediaBaseUrl)
    outputs.dir(outputDir)
    doLast {
        val pkgDir = outputDir.get().asFile.resolve("com/fox/music/core/common")
        pkgDir.mkdirs()
        pkgDir.resolve("FoxBuildConfig.kt").writeText(
            """
            |package com.fox.music.core.common
            |
            |/**
            | * 由 Gradle 任务 generateFoxBuildConfig 生成，请勿手改。
            | * 用于替代 AGP 的 BuildConfig —— 后者是 Android 专有产物，KMP 的 commonMain 引用不到。
            | */
            |object FoxBuildConfig {
            |    const val MEDIA_BASE_URL: String = "$mediaBaseUrl"
            |}
            |
            """.trimMargin()
        )
    }
}

kotlin {
    androidLibrary {
        namespace = "com.fox.music.core.common"
        compileSdk = 36
        minSdk = 26

        // AGP 9 的 KMP Android 库插件默认【不】处理 src/androidMain/res，
        // 只生成 generateAndroidMainEmptyResourceFiles，导致 R 类为空、
        // ImageExt.kt 里的 R.mipmap.ic_app / R.drawable.* 全部解析失败。
        // 打开 androidResources 后资源才会被合并并生成 R 类。
        androidResources {
            enable = true
        }

        withHostTestBuilder {}.configure {}

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateFoxBuildConfig)
            dependencies {
                api(project(":core:model"))
                // 注意：coroutines 的 android 适配器是 Android 专有，不能进 commonMain
                api(libs.kotlinx.coroutines.core)
            }
        }

        androidMain {
            dependencies {
                api(libs.androidx.core.ktx)
                api(libs.kotlinx.coroutines.android)
                api(libs.timber)
                api(libs.bundles.lifecycle)
                api(libs.utilcodex)
                api(libs.xxpermissions)
                api(libs.github.glide)
                api(libs.glide.transformations)
            }
        }

        named("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
