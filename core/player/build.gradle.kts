plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.core.player"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    api(libs.bundles.media3)
//    api(libs.videocache)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.bundles.coroutines)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
}
