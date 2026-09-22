import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:mapper —— 跨端映射层（DTO ↔ 领域模型）。
//
// 从 :core:data 抽出来的原因：:core:data 绑着 Retrofit / Room / WorkManager / Hilt，
// 而映射本身是零平台依赖的纯函数。抽出来后 iOS 端可以直接复用同一份
// MusicDto.toMusic()，不必再手写简化映射（此前 iosShared 里那份已删除）。
//
// 包名刻意保持 com.fox.music.core.data.mapper 不变 —— 全项目 69 处 import 零改动。
//
// 仍留在 :core:data 的：ChatLocalMappers / DownloadMapper —— 它们依赖 Room 实体
// （com.fox.music.core.database.entity），等 :core:database 跨端化后再合并。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    androidLibrary {
        namespace = "com.fox.music.core.mapper"
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
            implementation(project(":core:network"))
        }
    }
}
