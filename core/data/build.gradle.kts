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
    // :core:network 是跨端 DTO 层，Android 实现（ApiService / Retrofit / Hilt 装配）在 :core:network-android，
    // 后者 api 依赖前者，所以这里只依赖一个即可拿到全部类型。
    implementation(project(":core:network-android"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    
    // Coroutines
    implementation(libs.bundles.coroutines)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.work)

    implementation(libs.ffmpeg.kit.min.gpl)
    
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
