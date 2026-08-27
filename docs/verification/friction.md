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
