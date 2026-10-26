import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :feature:home —— 首页的跨端部分。
//
// 批次 14 把 paging-common / paging-compose 拉进 commonMain 之后，
// 首页那条 Flow<PagingData<Music>> 的「为你精选」列表也能进 commonMain 了。
//   commonMain：HomeContract + HOME_ROUTE + HomeContent（纯 Compose，含分页列表）
//   :feature:home-android：HomeViewModel（Hilt）+ hiltViewModel() 外壳
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
        namespace = "com.fox.music.feature.home"
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
            implementation(project(":core:domain"))
            implementation(project(":core:ui"))

            api(libs.paging.common)
            api(libs.paging.compose)
            // 首页头部「10月22日 · 周四」+ 按时段问候：java.time 在 K/N 不可用，统一走 kotlinx-datetime
            implementation(libs.kotlinx.datetime)

            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.materialIconsExtended)
        }
    }
}
