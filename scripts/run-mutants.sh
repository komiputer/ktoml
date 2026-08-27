#!/usr/bin/env bash
# Phase 0 negative-control gate (docs/verification/plan.md §"Phase 0"/§3).
#
# Applies each patch in verification/mutants/ to the verified/ kernel one at a time,
# runs verification (:ktoml-core:compileKotlinJvm, which fails the build on a Viper
# verification error per the log-scan gate in ktoml-core/build.gradle.kts), asserts
# that build FAILS, then reverts. A mutant whose build SUCCEEDS means either the
# mutant didn't change provable behaviour or the harness itself is broken -- in
# either case this script exits nonzero and names the offending mutant(s).
set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mutants_dir="$repo_root/verification/mutants"
target_file="$repo_root/ktoml-core/src/commonMain/kotlin/com/akuleshov7/ktoml/verified/Lexing.kt"

cd "$repo_root"

if [[ -n "$(git status --porcelain -- "$target_file")" ]]; then
    echo "run-mutants.sh: $target_file has uncommitted changes; refusing to run (would revert your work)." >&2
    exit 2
fi

failures=()
passed_when_should_fail=()

for patch in "$mutants_dir"/*.patch; do
    name="$(basename "$patch" .patch)"
    echo "=== $name ==="

    if ! git apply "$patch"; then
        echo "  FAILED TO APPLY -- treating as harness failure"
        failures+=("$name (patch did not apply)")
        continue
    fi

    if ./gradlew --no-daemon :ktoml-core:compileKotlinJvm > "/tmp/run-mutants-$name.log" 2>&1; then
        echo "  VERIFIED (should have failed) -- see /tmp/run-mutants-$name.log"
        passed_when_should_fail+=("$name")
    else
        echo "  rejected, as expected"
    fi

    git checkout -- "$target_file"
done

echo
if [[ ${#failures[@]} -gt 0 || ${#passed_when_should_fail[@]} -gt 0 ]]; then
    echo "run-mutants.sh: BROKEN HARNESS OR REGRESSION"
    for n in "${failures[@]}"; do echo "  patch error: $n"; done
    for n in "${passed_when_should_fail[@]}"; do echo "  verified when it should have failed: $n"; done
    exit 1
fi

echo "run-mutants.sh: all $(ls "$mutants_dir"/*.patch | wc -l) controls correctly rejected."
exit 0
