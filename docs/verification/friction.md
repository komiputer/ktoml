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
