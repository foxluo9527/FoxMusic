// :feature:profile-android —— 个人中心模块的安卓侧状态机与屏幕外壳。
//
// 与 :feature:profile 配对：后者放跨端路由常量与干净组件，这里放
//   1) 11 个 @HiltViewModel
//   2) 对应的屏幕外壳（LocalContext / Toast / BackHandler / uCrop 等平台能力）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.feature.profile.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // 用 api：app 模块要用到本模块的屏幕与路由常量
    api(project(":feature:profile"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:player"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.bundles.coroutines)

    implementation(libs.ucrop)
    implementation(libs.paging.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
