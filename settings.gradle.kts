pluginManagement {
    includeBuild("build-logic")
    repositories {
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
        google()
        mavenCentral()
    }
}

rootProject.name = "FoxMusic"

// App module
include(":app")

// Core modules
include(":core:common")
include(":core:model")
include(":core:ui")
include(":core:domain")
include(":core:data")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:player")
// Feature modules
include(":feature:home")
include(":feature:auth")
include(":feature:player")
include(":feature:playlist")
include(":feature:search")
include(":feature:discover")
include(":feature:social")
include(":feature:chat")
include(":feature:profile")
