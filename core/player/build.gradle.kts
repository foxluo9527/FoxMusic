plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.core.player"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    api(libs.bundles.media3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.bundles.coroutines)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
}
