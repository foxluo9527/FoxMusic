pluginManagement {
    repositories {
//        maven { url = uri("https://maven.aliyun.com/repository/releases") }
//        maven { url = uri("https://maven.aliyun.com/repository/google") }
//        maven { url = uri("https://maven.aliyun.com/repository/central") }
//        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
//        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google {
            content {
                includeGroupByRegex("com.android.*")
                includeGroupByRegex("com.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com.android.*")
                includeGroupByRegex("com.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github.*")
            }
        }
    }
}

rootProject.name = "FoxMusic"

// App module
include(":app")

// Core modules
include(":core:common")
include(":core:common-android")
include(":core:model")
include(":core:ui")
include(":core:domain")
include(":core:domain-android")
include(":core:data")
include(":core:mapper")
include(":core:network")
include(":core:network-android")
include(":core:database")
include(":core:datastore")
include(":core:player")
// iOS 宿主共享框架（SwiftUI 外壳 iosApp/ 消费的 FoxShared.framework 由它产出）
include(":iosShared")
// Feature modules
include(":feature:home")
include(":feature:home-android")
include(":feature:auth")
include(":feature:auth-android")
include(":feature:player")
include(":feature:playlist")
include(":feature:playlist-android")
include(":feature:search")
include(":feature:search-android")
include(":feature:discover")
include(":feature:discover-android")
include(":feature:social")
include(":feature:social-android")
include(":feature:chat")
include(":feature:profile")
include(":feature:profile-android")
include(":feature:automotive")
