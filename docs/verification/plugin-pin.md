# SnaKt (formver) plugin pin

- Checkout: jormungandr `origin/main` at 2f23a270.
- Published version: `0.1.0-2f23a270` (the checkout's root `build.gradle.kts` version set to it).
- Build: `./gradlew publishToMavenLocal -Dmaven.repo.local=<private repo> -x test`.
- Consume it the same way: `./gradlew :ktoml-core:compileKotlinJvm -Dmaven.repo.local=<private repo>`.
  The shared `~/.m2` is not used, so other formver builds cannot replace the pinned one.
  `scripts/gate-test.sh` reads the private repository path from `SNAKT_MAVEN_REPO`.

## Behaviour at this pin

- Every function is verified unless it is annotated `@NeverVerify` (body not verified) or
  `@NeverConvert` (not converted at all). Functions the plugin cannot convert are skipped with a warning.
- A proof failure is a compiler error, so `compileKotlinJvm` fails on it.
- Each function's verification is cut off after 300 seconds and reported as `VERIFICATION_TIMEOUT`.
  Change the limit with `formver { verificationTimeout(N) }`.

## Artifacts in <private repo>/org/jetbrains/kotlin/formver/

- formver.annotations-0.1.0-2f23a270
- formver.common-0.1.0-2f23a270
- formver.compiler-plugin-0.1.0-2f23a270 (shaded jar, ~100MB — bundles Silicon/Scala runtime)
- formver.gradle-plugin-0.1.0-2f23a270 (+ sources/javadoc jars)
- org.jetbrains.kotlin.formver.gradle.plugin-0.1.0-2f23a270 (Gradle plugin marker artifact)

## Consumer coordinates / setup (per README.md)

Plugin id: `org.jetbrains.kotlin.formver`, version `0.1.0-2f23a270`.

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
    id("org.jetbrains.kotlin.formver") version "0.1.0-2f23a270"
}

repositories {
    mavenCentral()
    mavenLocal()
}
```

Required Kotlin compiler tuning: the shaded compiler-plugin jar needs a larger metaspace, and Silicon
needs a deep stack. In the Kotlin daemon:
```kotlin
kotlin {
    kotlinDaemonJvmArgs = listOf("-Xss30m", "-XX:MaxMetaspaceSize=1g")
}
```
When compiling in-process (`-Pkotlin.compiler.execution.strategy=in-process`), pass the same limits to
Gradle: `"-Dorg.gradle.jvmargs=-Xmx6g -Xss30m -XX:MaxMetaspaceSize=2g"`.
