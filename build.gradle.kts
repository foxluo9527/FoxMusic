// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// AGP 9.0 ships built-in Kotlin support with a runtime dependency on a specific KGP version.
// The `kotlin` entry in gradle/libs.versions.toml only feeds the Compose compiler plugin and
// does NOT change the Kotlin compiler actually used. To pin a newer KGP/KSP we must declare
// them explicitly on the buildscript classpath here.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
