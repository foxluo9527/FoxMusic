import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 下 com.android.library 不能与 KMP 插件共存，改用 KMP 专用的 Android 库插件
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform)
    // 注意：本模块不能应用 Hilt 的 Gradle 插件 —— 它会报
    // "The Hilt Android Gradle plugin can only be applied to an Android project."
    // 因此也不再用 KSP。模块内的 @Module @InstallIn 类由 :app 侧的 Hilt 聚合处理发现，
    // 若发现不到会在 :app 编译期以 MissingBinding 报错（已验证通过）。
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

        androidMain.dependencies {
            api(libs.androidx.core.ktx)
            api(libs.kotlinx.coroutines.android)
            api(libs.timber)
            api(libs.bundles.lifecycle)
            api(libs.utilcodex)
            api(libs.xxpermissions)
            api(libs.github.glide)
            api(libs.glide.transformations)
            // 仅需注解（@Module / @InstallIn），不需要注解处理器
            implementation(libs.hilt.android)
        }

        named("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
