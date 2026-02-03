plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.core.domain"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:common"))
    api(project(":core:model"))
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    
    // Coroutines
    implementation(libs.bundles.coroutines)
    
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
