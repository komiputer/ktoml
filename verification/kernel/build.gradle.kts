plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.formver")
}

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    jvmToolchain(21)
    sourceSets.main {
        kotlin.srcDir("../../ktoml-core/src/commonMain/kotlin/com/akuleshov7/ktoml/verified")
    }
}

formver {
    // A control changes one function; the cache keeps the others from being verified again. The key
    // covers the whole Viper program, the plugin and the verifier.
    verificationCacheDirectory(layout.buildDirectory.dir("verification-cache").get().asFile.path)
}
