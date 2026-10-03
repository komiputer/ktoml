# SnaKt (formver) plugin pin

- Checkout: jormungandr `origin/main` at 1f8c164a.
- Published version: `0.1.0-1f8c164a` (the checkout's root `build.gradle.kts` version set to it).
- Build: `./gradlew publishToMavenLocal -Dmaven.repo.local=<private repo> -x test`.
- Consume it the same way: `./gradlew :ktoml-core:compileKotlinJvm -Dmaven.repo.local=<private repo>`.
  The shared `~/.m2` is not used, so other formver builds cannot replace the pinned one.

## Artifacts in <private repo>/org/jetbrains/kotlin/formver/

- formver.annotations-0.1.0-1f8c164a
- formver.common-0.1.0-1f8c164a
- formver.compiler-plugin-0.1.0-1f8c164a (shaded jar, ~100MB — bundles Silicon/Scala runtime)
- formver.gradle-plugin-0.1.0-1f8c164a (+ sources/javadoc jars)
- org.jetbrains.kotlin.formver.gradle.plugin-0.1.0-1f8c164a (Gradle plugin marker artifact)

## Consumer coordinates / setup (per README.md)

Plugin id: `org.jetbrains.kotlin.formver`, version `0.1.0-1f8c164a`.

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}
```

`build.gradle.kts`:
```kotlin
plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.kotlin.formver") version "0.1.0-1f8c164a"
}

repositories {
    mavenCentral()
    mavenLocal()
}
```

Required Kotlin daemon tuning (shaded compiler-plugin jar needs larger metaspace/stack, else `OutOfMemoryError: Metaspace` after ~a dozen compiles):
```kotlin
kotlin {
    kotlinDaemonJvmArgs = listOf("-Xss30m", "-XX:MaxMetaspaceSize=1g")
}
```
