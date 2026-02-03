plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.fox.music.core.database"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    
    // Room
    api(libs.bundles.room)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    
    // Coroutines
    implementation(libs.bundles.coroutines)
    
    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
