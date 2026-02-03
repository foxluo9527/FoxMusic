plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fox.music.core.ui"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    api(platform(libs.androidx.compose.bom))
    api(libs.bundles.compose)
    debugImplementation(libs.bundles.compose.debug)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.coil.compose)
    api(libs.bundles.lifecycle)
    implementation(libs.lottie.compose)
    testImplementation(libs.junit)
}
