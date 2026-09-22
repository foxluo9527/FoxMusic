import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.fox.music"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fox.music"
        minSdk = 26
        targetSdk = 37
        versionCode = 112
        versionName = "1.2.0"

        val buglyAppId = localProperties.getProperty("BUGLY_APP_ID")
            ?: (project.findProperty("BUGLY_APP_ID") as String?)
            ?: System.getenv("BUGLY_APP_ID")
            ?: ""
        val buglyAppKey = localProperties.getProperty("BUGLY_APP_KEY")
            ?: (project.findProperty("BUGLY_APP_KEY") as String?)
            ?: System.getenv("BUGLY_APP_KEY")
            ?: ""
        buildConfigField("String", "BUGLY_APP_ID", "\"$buglyAppId\"")
        buildConfigField("String", "BUGLY_APP_KEY", "\"$buglyAppKey\"")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.coil.compose)
    // Core modules
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    // UseCase 的 Hilt 显式装配（UseCaseModule，78 个 @Provides）。
    // :core:domain 是 KMP 模块，无法承载 Dagger 产物。
    implementation(project(":core:domain-android"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":core:player"))
    // :core:network 是跨端 DTO 层，Android 实现（ApiService / Retrofit / Hilt 装配）在 :core:network-android，
    // 后者 api 依赖前者，所以这里只依赖一个即可拿到全部类型。
    implementation(project(":core:network-android"))
    // Feature modules
    implementation(project(":feature:home-android"))
    implementation(project(":feature:auth-android"))
    implementation(project(":feature:player"))
    implementation(project(":feature:playlist-android"))
    implementation(project(":feature:search-android"))
    implementation(project(":feature:discover-android"))
    implementation(project(":feature:social-android"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:profile-android"))
    implementation(project(":feature:automotive"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.ucrop)

    // Lifecycle
    implementation(libs.bundles.lifecycle)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.lifecycle.process)
    debugImplementation(libs.bundles.compose.debug)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.work)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Logging
    implementation(libs.timber)

    // Crash reporting
    implementation(libs.bugly.crashreport)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
