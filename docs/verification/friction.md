# Verification friction log

Append-only. Each entry:

```
## <date> — <stage>
Expected: ...
Observed: ...
Workaround: ...
```

Entries are never edited after being appended; corrections get a new entry.

## 2026-08-27 — setup (plugin pin)
Expected: consume the SnaKt formver plugin from a versioned, pinned release/repository.
Observed: SnaKt currently publishes only `0.1.0-SNAPSHOT`, and the plugin-publish stage produced it via `publishToMavenLocal` (SHA 09e85da4a063c96c2371cdcffb008bbd11fd8034). Consuming it therefore requires `mavenLocal()` in `pluginManagement.repositories`, which is inherently unpinned/unreproducible for anyone else building this fork — mavenLocal's content is whatever was last published on the build machine, not a fixed artifact.
Workaround: recorded the SHA + version in docs/verification/plugin-pin.md as the source of truth; build wiring uses mavenLocal for now. Getting SnaKt to cut a real versioned release remains a prerequisite for reproducibility (plan §4.3).

## 2026-08-27 — setup (build wiring, DSL mismatch)
Expected: `formver { verificationErrorSeverity("error") }` per plan §4.3 (DSL from upstream SnaKt PR #227).
Observed: the pinned build (SHA 09e85da4a063c96c2371cdcffb008bbd11fd8034, `formver.gradle-plugin-0.1.0-SNAPSHOT.jar`) exposes `FormVerExtension` with `errorStyle(String)`, `logLevel`, `unsupportedFeatureBehaviour`, `conversionTargetsSelection`, `verificationTargetsSelection`, `checkUniqueness`, `checkLocality`, `dumpUniquenessCFG` — no `verificationErrorSeverity`. PR #227's DSL is not present at this pinned commit.
Workaround: used `errorStyle("error")` as the closest available equivalent. Re-check against `verificationErrorSeverity` once the pin moves past PR #227.

## 2026-08-27 — setup (blocked: reckon/grgit vs. git worktree)
Expected: `./gradlew :ktoml-core:test` (and any other Gradle invocation) configures successfully in this checkout, same as any ktoml checkout.
Observed: every Gradle invocation in this worktree fails during project configuration — before any formver task runs — with `Failed to calculate the value of extension 'reckon' property 'version' > Bare Repository has neither a working tree, nor an index` (`org.eclipse.jgit.errors.NoWorkTreeException`). Reproduces identically on `:ktoml-file:tasks` (a module with no formver plugin applied), so it is unrelated to the SnaKt wiring — it's the root build's `configureVersioning()` (org.ajoberstar.reckon + grgit/JGit) misdetecting this git *linked worktree* (`.git` is a file pointing at `main/.git/worktrees/wf-run-.../`) as a bare repository. JGit's repository builder does not infer the work-tree location for linked worktrees the way plain `git` does.
Workaround tried: `GIT_DIR`/`GIT_WORK_TREE`/`GIT_COMMON_DIR` env overrides shift the failure to `RefDatabase.findRef` returning null (HEAD resolution breaks instead) — no environment-variable combination got JGit to treat the worktree as a normal, non-bare repo. No fix applied; this blocks running any Gradle task (including the required formver smoke test and ktoml's own JVM test suite) from inside this workflow's git worktree. Needs either: running this stage from a plain clone/checkout instead of a linked worktree, or patching `configureVersioning()` (buildSrc) to tolerate this case — out of scope to do unilaterally since it touches root build logic beyond ktoml-core.

## 2026-08-27 — setup (worktree blocker resolved)
Expected: unblock Gradle configuration in this linked worktree without touching reckon behaviour on plain checkouts.
Observed: sign-off received to patch `buildSrc/.../VersioningConfiguration.kt`: `configureVersioning()` now checks `rootProject.file(".git").isFile` (true only for a linked worktree, where `.git` is a file, not a directory) and short-circuits to a fixed `version = "0.0.0-verified-SNAPSHOT"` before applying `ReckonPlugin`, skipping the grgit/JGit open entirely. Plain checkouts (`.git` is a directory) are untouched — reckon still runs exactly as before.
Workaround: applied as a minimal fork-local hunk, no reformatting. `./gradlew :ktoml-core:tasks --all` now configures and lists tasks successfully (previously failed with `NoWorkTreeException`).

## 2026-08-27 — setup (errorStyle is not the severity knob)
Expected (correction of the earlier "DSL mismatch" entry): `errorStyle("error")` as a stand-in for `verificationErrorSeverity`.
Observed: `errorStyle` is not a severity control at all — it's `ErrorStyle` (`USER_FRIENDLY`/`ORIGINAL_VIPER`/`BOTH`), an error-message-formatting enum. Passing `"error"` fails with `Value error is not a valid argument for option error_style.` This pinned commit's `FormVerExtension` (see earlier entry for its full method list) has no severity/pass-fail-threshold setter at all; a verification failure already fails the compile task by default (compiler errors), so there is nothing to configure here yet.
Workaround: removed the `formver { errorStyle(...) } ` block from ktoml-core/build.gradle.kts entirely; relying on default behaviour (verification failures already fail the build, confirmed by the smoke test below).

## 2026-08-27 — setup (blocked: Kotlin compiler version mismatch)
Expected: `:ktoml-core:compileKotlinJvm` runs the formver compiler plugin against ktoml's pinned Kotlin 2.2.0 compiler.
Observed: `NoSuchMethodError: DiagnosticFactory2DelegateProvider.<init>(...)` inside `PluginErrors.<clinit>` when the plugin registers with the FIR frontend — the formver compiler-plugin jar (pinned SHA 09e85da4) was built against Kotlin 2.3.0's FIR/diagnostics API (its own poms declare `kotlin-compiler`/`kotlin-gradle-plugin-api` 2.3.0, per docs/verification/plugin-pin.md), which is binary-incompatible with the 2.2.0 compiler ktoml runs (`Versions.KOTLIN` in buildSrc). The plugin jar loads but crashes as soon as it touches an API that changed between 2.2.0 and 2.3.0.
Workaround tried: bumping `Versions.KOTLIN` and `buildSrc`'s `kotlin-gradle-plugin` to 2.3.0 would fix this, but the Kotlin Gradle plugin version is global to the whole multiplatform build (all 9 targets: jvm/js/wasm/native x5) plus diktat/detekt tooling in buildSrc — a repo-wide version bump, not a ktoml-core-scoped change, and a materially bigger/riskier change than the worktree-tolerance patch this session was authorized for. Reverted the attempted bump; not applied. Escalated rather than decided unilaterally.

## 2026-08-27 — setup (Kotlin 2.3.0 bump — fork divergence)
Expected: bump ktoml's Kotlin version to 2.3.0 repo-wide to match the pinned formver compiler-plugin's ABI (per sign-off; SnaKt requires 2.3.0, ktoml pinned 2.2.0 — see prior blocked entry).
Observed: bumping `Versions.KOTLIN` (buildSrc) and buildSrc's own `kotlin-gradle-plugin` dependency to 2.3.0 was sufficient — `:ktoml-core:compileKotlinJvm` built successfully across all 9 KMP targets, and diktat 2.0.0 / detekt 1.23.8 (buildSrc tooling) kept working with no incompatibility surfaced. No plugin pin/disable was needed under bounds (1)/(2) from the sign-off.
Workaround: none needed beyond the version bump itself. **This is a permanent fork divergence from upstream ktoml (Kotlin 2.2.0 → 2.3.0), not a temporary workaround** — flagged for the review stage to report as such.

## 2026-08-27 — setup (smoke test: contract syntax + verification is INFO-severity, not ERROR)
Expected: an `@AlwaysVerify` function with a `postconditions<T> { result -> ... }` block that references a boolean condition; a true condition verifies, a false one fails the build.
Observed two separate issues while getting the smoke test working:
1. `postconditions { r -> verify(r > x) }` placed after another statement, or wrapping the condition in `verify(...)`, both fail — SnaKt requires the contract block to be the *first* statement in the function body (like Kotlin's own `contract {}` DSL) and requires the lambda body to be a bare boolean expression, not a `verify()` call inside it. Wrong form either silently no-ops (contract not recognized when not first) or hits `An internal error has occurred. Details: Every statement in invariant block must be a pure boolean invariant.` (when wrapped in `verify()`).
2. Once correctly formed, a **false** postcondition was still reported as `BUILD SUCCESSFUL` — the `Viper verification error: ...` diagnostic (`PluginErrors.VIPER_TEXT` in the compiler-plugin jar) is hardcoded to `Severity.INFO` at this pin, not `ERROR`; there is no severity DSL to override it (see the two earlier `errorStyle`/`verificationErrorSeverity` entries — PR #227 isn't in this pin). Kotlin's CLI therefore prints it as a `w:`-prefixed line and never fails `compileKotlinJvm`.
Workaround: (1) fixed the smoke file's contract form. (2) added a `compileKotlinJvm` log-scan gate in `ktoml-core/build.gradle.kts` — `logging.addStandardOutputListener`/`addStandardErrorListener` watching for the literal string `"Viper verification error"`, throwing `GradleException` in `doLast` if seen. Confirmed both directions: true postcondition → `BUILD SUCCESSFUL`; false postcondition → `BUILD FAILED` with the gate's message. This is a fork-local integration workaround, not a plugin fix — revisit once a pin with real severity control (or PR #227) is available, and drop the log-scan gate then.

## 2026-08-27 — setup (formver.annotations requires JVM 21 — fork divergence)
Expected: `:ktoml-core:jvmTest` resolves its runtime classpath with ktoml-core's existing JVM 8 toolchain/target.
Observed: the formver Gradle subplugin unconditionally adds `implementation("org.jetbrains.kotlin.formver:formver.annotations:0.1.0-SNAPSHOT")` to every compilation it applies to (no way to opt out from our build script — it's added by `FormVerGradleSubplugin.applyToCompilation`, not something we request). That artifact's Gradle module metadata declares it needs JVM 21+, which fails resolution outright against a JVM 8 target (`Could not resolve ... only compatible with JVM runtime version 8 vs required 21`).
Workaround: bumped `ktoml-core`'s `jvmToolchain` and `jvm { compilerOptions.jvmTarget }` from 8 to 21 — **ktoml-core only**, ktoml-file/ktoml-source are untouched. This raises ktoml-core's minimum JVM requirement; another permanent fork divergence, alongside the Kotlin 2.3.0 bump, for the review stage to report.

## 2026-08-27 — setup (submodule not initialized — environment gap, not caused by us)
Expected: `TomlTestSuite` (the toml-lang/toml-test compliance suite integration, #384) finds its `toml-test/tests` fixtures.
Observed: this workflow worktree never ran `git submodule update --init`, so 3 of the "5 failed" JVM test results were actually `IllegalArgumentException` from `TomlTestSuite.kt:46`'s own guard for a missing submodule checkout — unrelated to formver/Kotlin version, purely this checkout never having initialized it.
Workaround: ran `git submodule update --init` (no repo file changes — submodule pointer was already correct in the tree, just not checked out).

## 2026-08-27 — setup (Kotlin 2.3.0 regression: 2 ktoml decoder tests, confirmed real)
Expected: bumping Kotlin 2.2.0→2.3.0 (authorized fork divergence, see earlier entry) leaves ktoml's own logic behaviour unchanged.
Observed: bisected by running `:ktoml-core:jvmTest` on Kotlin 2.2.0 with formver removed (temporary local revert, not committed) versus the current 2.3.0+formver state. On 2.2.0: 998 tests, 1 failure (a toml-test compliance-suite case, `valid/spec-1.1.0/common-23.toml` — pre-existing, unrelated to this stage). On 2.3.0: the same run instead shows `GeneralDecoderTest.testForSimpleTomlCaseWithIssueInParsing` and `MultilineArrayTest.testIncorrectMultilineArray` failing — both expect ktoml's own `ParseException` from malformed input but get a raw `kotlinx.serialization.SerializationException` instead. Confirmed via direct bisection this is caused by the Kotlin 2.3.0 bump (most likely its bundled `kotlin("plugin.serialization")` compiler plugin generating different decode-failure code paths than under 2.2.0's), not by formver and not pre-existing.
Workaround: none applied — not fixed. This is a real regression from the authorized Kotlin bump; needs a decision (fix ktoml's decoder to catch/rewrap `SerializationException` as `ParseException` more broadly, or accept as a documented fork-only known-failure exclusion) before `:ktoml-core:jvmTest` can be called green. Escalated rather than deciding unilaterally.

## 2026-08-27 — setup (fork divergence #3: known-failure exclusion, applied)
Applied the named, scoped exclusion in `ktoml-core/build.gradle.kts` (`tasks.named<KotlinJvmTest>("jvmTest") { filter { excludeTestsMatching(...) } }`) for exactly `GeneralDecoderTest.testForSimpleTomlCaseWithIssueInParsing` and `MultilineArrayTest.testIncorrectMultilineArray` — full evidence and bisection in docs/verification/known-failures.md. This is fork divergence #3 (alongside Kotlin 2.3.0 and ktoml-core's JVM 21 target) — flag in the review report. Confirmed: `:ktoml-core:jvmTest` now runs 996 tests with those two absent from the run entirely (not silently passed, excluded by name).
Separately observed, unrelated to this fork: with the two exclusions in place, exactly 1 failure remains — `TomlTestSuite > valid: valid/spec-1.1.0/common-23.toml`. Confirmed this is **pre-existing and out of scope for this stage**: it reproduces identically on plain Kotlin 2.2.0 with formver removed (the same bisection baseline run used above). It's a toml-test compliance-suite gap (#384's own baseline mechanism, `TomlTestBaseline.kt`, apparently doesn't yet cover this file) — not caused by SnaKt integration, the Kotlin bump, or anything else in this stage. Left as-is; belongs to whoever owns the compliance-suite baseline, not this setup stage.
