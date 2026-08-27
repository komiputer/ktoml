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
