import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 下 com.android.library 不能与 KMP 插件共存于同一模块，
    // 必须改用 com.android.kotlin.multiplatform.library（Android 目标写在 kotlin { androidLibrary { } }）。
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // iOS 真机 + Apple Silicon 模拟器。iosMain 共享代码放 src/iosMain/kotlin。
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "com.fox.music.core.model"
        compileSdk = 36
        minSdk = 26

        // 开启 Android 宿主机单测源集（src/androidHostTest/kotlin）
        withHostTestBuilder {}.configure {}

        // 与改造前 compileOptions 的 JavaVersion.VERSION_17 对齐。
        // jvmTarget 是 JVM 专属选项，KMP 顶层的 compilerOptions 里没有，必须按 compilation 配置。
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            // paging-common 是 KMP（含 iOS 变体），commonMain 暴露后
            // core:domain 的 PagingSource/PagingData 才能进跨端源集。
            api(libs.paging.common)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidMain.dependencies {
            // 这两条本模块自身用不到（模块内 0 处 Paging 符号），但与改造前保持一致：
            // core:domain 的 28 个文件靠这里的 api 透传拿到 androidx.paging，
            // feature:social 只自管 paging、feature:profile 只自管 paging.compose。
            // 删除会连带编译失败，故暂留，待下游各自声明后单独清理。
            api(libs.paging)
            api(libs.paging.compose)
        }

        // androidHostTest 由 AGP 的 androidLibrary { withHostTestBuilder } 动态创建，
        // KMP 插件不会为它生成类型安全访问器，只能用 named() 按名字取。
        named("androidHostTest") {
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}
