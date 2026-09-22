// 本模块是 :core:network 的 **Android 实现层**。
//
// 为什么要单独拆一个模块：AGP 的 KMP Android 库插件（com.android.kotlin.multiplatform.library）
// 只注册 Kotlin 编译任务，没有任何 Java 编译任务。而 Hilt/Dagger 的注解处理器产出的
// hilt_aggregated_deps 存根与 *Factory.java 都是 **Java** 源文件，因此在 KMP 模块里永远编不进
// 产物 —— 结果就是 :app 的 Hilt 聚合发现不到 @Module，报一大片 MissingBinding。
//
// 结论（迁移期间必须遵守）：
//   任何带 @Module / @InstallIn / @Provides / @Binds / @Inject / @HiltViewModel 的 Kotlin 文件
//   都必须放在普通 Android 库模块（com.android.library）里，不能放进 KMP 模块。
//
// 所以本模块用回普通的 com.android.library + Hilt 插件，:core:network 只留跨端可复用的
// DTO / 纯逻辑。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.core.network.android"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 跨端契约与 DTO 在 :core:network（KMP）。用 api 让下游只依赖本模块即可拿到全部类型。
    api(project(":core:network"))

    api(libs.bundles.network)
    // Gson 仅用于 ErrorParser 解析后端错误体
    implementation(libs.gson)
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.timber)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
