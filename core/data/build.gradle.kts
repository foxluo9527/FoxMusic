plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.core.data"
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
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    
    // Coroutines
    implementation(libs.bundles.coroutines)
    
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
