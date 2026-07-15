plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val mediaBaseUrl = providers.gradleProperty("FOX_MUSIC_MEDIA_BASE_URL")
    .orElse(providers.environmentVariable("FOX_MUSIC_MEDIA_BASE_URL"))
    .orElse("http://39.106.30.151:9000")
    .get()

android {
    namespace = "com.fox.music.core.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MEDIA_BASE_URL", "\"$mediaBaseUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    api(libs.androidx.core.ktx)
    api(libs.bundles.coroutines)
    api(libs.timber)
    api(libs.bundles.lifecycle)
    api(libs.utilcodex)
    api(libs.xxpermissions)
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    api(libs.github.glide)
    api(libs.glide.transformations)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
