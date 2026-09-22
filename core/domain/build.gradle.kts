import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:domain —— 跨端可复用的领域层：仓库接口、UseCase、纯逻辑。
//
// UseCase 的 Hilt 装配在 :core:domain-android（一个 @Module + 78 个 @Provides 显式装配）。
// 之所以拆开：KMP 模块没有 Java 编译任务，而 Hilt/Dagger 生成的存根与工厂是 Java，
// 放进来会被静默丢弃并导致 :app 侧 MissingBinding（同 :core:network-android 的处理）。
// 因此本模块的 UseCase 一律去掉 @Inject 构造函数，改由 :core:domain-android 显式 new。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform)
}

kotlin {
    androidLibrary {
        namespace = "com.fox.music.core.domain"
        compileSdk = 36
        minSdk = 26

        withHostTestBuilder {}.configure {}

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            api(project(":core:model"))
            implementation(libs.kotlinx.coroutines.core)
        }

        // 分页（PagingSource/Pager/PagingData）与 android.net.Uri、java.io.File
        // 相关的 5 个仓库接口 + 18 个 UseCase 放 androidMain，暂不做跨端化。
        androidMain.dependencies {
            implementation(libs.paging)
        }

        // 纯逻辑测试放 commonTest：用 kotlin("test") 而非 JUnit，
        // 将来加 iOS target 后同一批用例可直接在 iOS 上跑。
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
