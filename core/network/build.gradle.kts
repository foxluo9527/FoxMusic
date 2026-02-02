plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        
        buildConfigField("String", "BASE_URL", "\"http://39.106.30.151:9000/\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    
    // Network
    api(libs.bundles.network)
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    
    // Coroutines
    implementation(libs.bundles.coroutines)
    
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
