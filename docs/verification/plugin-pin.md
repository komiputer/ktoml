# SnaKt (formver) plugin: publishToMavenLocal

- Checkout commit SHA: 09e85da4a063c96c2371cdcffb008bbd11fd8034
- Published version: 0.1.0-SNAPSHOT
- Build: `./gradlew publishToMavenLocal --max-workers=2 --no-daemon` — BUILD SUCCESSFUL in 2m, no errors/friction.

## Artifacts landed in ~/.m2/repository/org/jetbrains/kotlin/formver/

- formver.annotations-0.1.0-SNAPSHOT
- formver.common-0.1.0-SNAPSHOT
- formver.compiler-plugin-0.1.0-SNAPSHOT (shaded jar, ~100MB — bundles Silicon/Scala runtime)
- formver.gradle-plugin-0.1.0-SNAPSHOT (+ sources/javadoc jars)
- org.jetbrains.kotlin.formver.gradle.plugin-0.1.0-SNAPSHOT (Gradle plugin marker artifact)

## Consumer coordinates / setup (per README.md)

Plugin id: `org.jetbrains.kotlin.formver`, version `0.1.0-SNAPSHOT`.

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
    id("org.jetbrains.kotlin.formver") version "0.1.0-SNAPSHOT"
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

## Friction

None. Build completed cleanly with `--max-workers=2 --no-daemon`; formver.annotations is now an automatic dependency of the Gradle plugin per commit 09e85da4 already on this checkout. Repo working tree untouched (nothing committed/changed).
