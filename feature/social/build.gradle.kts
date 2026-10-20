import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :feature:social —— 社区/动态/发帖的跨端部分（KMP）。
//
//   commonMain：社区广场与帖子详情的 Contract（State/Intent/Effect）+ 纯 Compose Content
//     （批次 30：把社区版块迁到 iOS，两端共用同一份 UI 与契约）。
//   :feature:social-android：8 个 @HiltViewModel + 全部屏幕外壳 + CreatePostSelectionStore
//     （CreatePostSelectionStore 用 @Inject / @ActivityRetainedScoped，属 Hilt 注解，
//      只能放安卓侧，不可进 commonMain）。
//   发帖页涉及图片选择/上传/裁剪等平台能力，仍留在 :feature:social-android。
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

            // 广场/详情的列表都是分页流，契约里就有 Flow<PagingData<Post>>
            api(libs.paging.common)
            api(libs.paging.compose)
            // 帖子时间格式化（替代 JVM-only 的 java.time）
            implementation(libs.kotlinx.datetime)
        }
    }
}
