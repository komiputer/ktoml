# scanners-suite run notes

`scripts/run-mutants.sh` executed against all 58 patches in `verification/mutants/`
(mutants + verify(false) probes + precondition-deletion probes).

Result: all 58 controls correctly rejected by verification (`:ktoml-core:compileKotlinJvm`
failed for every patch, as expected). Exit code 0. No harness failures, no patch
application failures, no control that unexpectedly passed verification.

Wall-clock runtime: suite was launched, then polled at intervals across a period of
roughly 24h of wall-clock time due to session interruptions (see friction.md); actual
compute time was not measured precisely because of this. No regression or anomaly
was found in the final log (`/tmp/run-mutants-full.log`).
