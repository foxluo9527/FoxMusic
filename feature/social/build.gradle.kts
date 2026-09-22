import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :feature:social —— 社区/动态/发帖的跨端部分（KMP 骨架）。
//
//   当前 commonMain 暂为空壳（社交屏全部依赖 BackHandler / LocalContext / Toast /
//   PostNoteGridCard 等安卓侧组件，留待后续批次拆分出纯 Compose 部分）。
//   :feature:social-android：8 个 @HiltViewModel + 全部屏幕外壳 + CreatePostSelectionStore
//     （CreatePostSelectionStore 用 @Inject / @ActivityRetainedScoped，属 Hilt 注解，
//      只能放安卓侧，不可进 commonMain）。
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
        namespace = "com.fox.music.feature.social"
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
            // 注意：core:datastore 是 android.library（非 KMP），不可进 commonMain，
            // 已在 :feature:social-android 的 androidMain 侧依赖。

            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.materialIconsExtended)
        }
    }
}
