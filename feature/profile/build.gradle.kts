import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :feature:profile —— 个人中心 / 设置 / 用户主页的跨端部分。
//
//   commonMain：路由常量（LyricSettingsRoutes / UserHomeRoutes）+ 干净组件
//              （SettingsItems / UserHomeCoverSpec）
//   :feature:profile-android：11 个 @HiltViewModel + 全部屏幕外壳
//     （EditProfileScreen / UserHomeScreen 依赖 android.net.Uri + uCrop，
//      ThemeSettingsScreen / DownloadScreen 等依赖 LocalContext / Toast，只能留安卓侧）
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
        namespace = "com.fox.music.feature.profile"
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

            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.materialIconsExtended)
        }
    }
}
