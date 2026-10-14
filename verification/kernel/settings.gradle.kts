// Standalone build that compiles and verifies only ktoml-core's verified/ sources, so a negative control
// costs one small verification run instead of a full ktoml-core compilation. scripts/run-mutants.sh
// passes ktoml's Kotlin version as `kotlinVersion` and the plugin version pinned in
// ktoml-core/build.gradle.kts as `formverVersion`.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
    plugins {
        kotlin("jvm") version providers.gradleProperty("kotlinVersion").get()
        id("org.jetbrains.kotlin.formver") version providers.gradleProperty("formverVersion").get()
    }
}

rootProject.name = "ktoml-verified-kernel"
