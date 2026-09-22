// 本模块是 :core:domain 的 **Android 装配层**。
//
// 为什么要单独拆一个模块：AGP 的 KMP Android 库插件（com.android.kotlin.multiplatform.library）
// 只注册 Kotlin 编译任务，没有任何 Java 编译任务。而 Hilt/Dagger 的注解处理器产出的
// hilt_aggregated_deps 存根与 *Factory.java 都是 **Java** 源文件，因此在 KMP 模块里永远编不进
// 产物 —— 结果就是 :app 的 Hilt 聚合发现不到 @Module，报一大片 MissingBinding。
//
// :core:domain 的 78 个 UseCase 已全部去掉 @Inject 构造函数（否则 iOS 侧的 commonMain
// 也会被迫依赖 javax.inject），改由本模块的 di/UseCaseModule 用 78 个 @Provides 显式 new。
// UseCase 构造函数签名变化时，需同步更新 UseCaseModule。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.fox.music.core.domain.android"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 跨端领域层（仓库接口 + UseCase 类）在 :core:domain（KMP）。
    // 用 api 让下游只依赖本模块即可拿到全部类型。
    api(project(":core:domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
