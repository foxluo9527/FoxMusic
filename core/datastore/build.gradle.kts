plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.core.datastore"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    api(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.bundles.coroutines)
    testImplementation(libs.junit)
}
