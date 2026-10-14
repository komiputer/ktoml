#!/usr/bin/env bash
# Negative-control gate (docs/verification/plan.md §"Phase 0"/§3), covering every file under
# ktoml-core's verified/ source set (Lexing.kt, Trims.kt, ...).
#
# Applies each patch in verification/mutants/ to the verified/ kernel one at a time,
# runs verification in the standalone verification/kernel build (compileKotlin fails on a proof
# failure), asserts that build FAILS with a compiler error, then reverts. A mutant whose build
# SUCCEEDS means either the mutant didn't change provable behaviour or the harness itself is
# broken -- in either case this script exits nonzero and names the offending mutant(s). The
# unmodified kernel must verify first, and a failed build without a compiler error counts as a
# harness failure, so a broken build cannot pass as a rejected control.
set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
maven_repo="${SNAKT_MAVEN_REPO:-$HOME/tmp/volatile/snakt-m2}"
mutants_dir="$repo_root/verification/mutants"
verified_dir="$repo_root/ktoml-core/src/commonMain/kotlin/com/akuleshov7/ktoml/verified"
log_dir="$repo_root/verification/kernel/build/run-mutants"

cd "$repo_root"

kotlin_version="$(sed -n 's/.*const val KOTLIN = "\(.*\)".*/\1/p' buildSrc/src/main/kotlin/com/akuleshov7/Versions.kt)"
formver_version="$(sed -n 's/.*id("org.jetbrains.kotlin.formver") version "\(.*\)".*/\1/p' ktoml-core/build.gradle.kts)"
if [[ -z "$kotlin_version" || -z "$formver_version" ]]; then
    echo "run-mutants.sh: could not read the Kotlin version or the formver pin." >&2
    exit 2
fi
mkdir -p "$log_dir"

# Verifies the kernel; the build log goes to $1.
verify_kernel() {
    ./gradlew -p verification/kernel --no-daemon compileKotlin -Dmaven.repo.local="$maven_repo" \
        -PkotlinVersion="$kotlin_version" -PformverVersion="$formver_version" \
        -Pkotlin.compiler.execution.strategy=in-process "-Dorg.gradle.jvmargs=-Xmx4g -Xss30m -XX:MaxMetaspaceSize=1g" \
        > "$1" 2>&1
}

if [[ -n "$(git status --porcelain -- "$verified_dir")" ]]; then
    echo "run-mutants.sh: $verified_dir has uncommitted changes; refusing to run (would revert your work)." >&2
    exit 2
fi

# Controls that no specification can make fail, with the reason. Such a control either verifies or
# runs out of verification time; a proof failure counts against it. A control belongs here only when
# the reason is a stated tooling limit -- not "we could not work out how to strengthen the spec".
# Anything not listed here that verifies is a failure.
#
# probe-precondition-getCountOfOccurrencesOfSubstring: the deleted precondition is
#   `substring.length >= 1`. With an empty substring, `matchesAt(s, i, "")` holds and the scan
#   advances by `substring.length == 0`, so the loop never progresses and every empty-substring path
#   diverges. SnaKt proves partial correctness of loops only, and a divergent path satisfies every
#   postcondition vacuously -- so no postcondition can be violated on the paths the precondition
#   rules out. The precondition's job is termination, which the tooling cannot check. The verifier
#   does not finish this function without the precondition, even with a 1200-second limit.
expected_unfalsifiable=(
    "probe-precondition-getCountOfOccurrencesOfSubstring"
)

if ! verify_kernel "$log_dir/baseline.log"; then
    echo "run-mutants.sh: the unmodified kernel does not verify -- see $log_dir/baseline.log" >&2
    exit 1
fi

failures=()
passed_when_should_fail=()
expected_passes=()

for patch in "$mutants_dir"/*.patch; do
    name="$(basename "$patch" .patch)"
    echo "=== $name ==="

    if ! git apply "$patch"; then
        echo "  FAILED TO APPLY -- treating as harness failure"
        failures+=("$name (patch did not apply)")
        continue
    fi

    is_expected=0
    for expected in "${expected_unfalsifiable[@]}"; do
        [[ "$name" == "$expected" ]] && is_expected=1
    done

    log="$log_dir/$name.log"
    if verify_kernel "$log"; then
        if [[ $is_expected -eq 1 ]]; then
            echo "  verified -- expected, see the reason in this script's expected_unfalsifiable list"
            expected_passes+=("$name")
        else
            echo "  VERIFIED (should have failed) -- see $log"
            passed_when_should_fail+=("$name")
        fi
    elif [[ $is_expected -eq 1 ]] && grep -q '^e: .*did not finish within' "$log" \
        && ! grep '^e: ' "$log" | grep -qv 'did not finish within'; then
        echo "  timed out -- expected, see the reason in this script's expected_unfalsifiable list"
        expected_passes+=("$name")
    elif [[ $is_expected -eq 1 ]]; then
        echo "  REJECTED, but listed as unfalsifiable -- remove it from expected_unfalsifiable"
        failures+=("$name (rejected while listed as unfalsifiable)")
    elif ! grep -q '^e: ' "$log"; then
        echo "  BUILD FAILED WITHOUT A COMPILER ERROR -- see $log"
        failures+=("$name (build failed without a compiler error)")
    else
        echo "  rejected, as expected"
    fi

    git checkout -- "$verified_dir"
done

echo
if [[ ${#failures[@]} -gt 0 || ${#passed_when_should_fail[@]} -gt 0 ]]; then
    echo "run-mutants.sh: BROKEN HARNESS OR REGRESSION"
    for n in "${failures[@]}"; do echo "  patch error: $n"; done
    for n in "${passed_when_should_fail[@]}"; do echo "  verified when it should have failed: $n"; done
    exit 1
fi

total=$(ls "$mutants_dir"/*.patch | wc -l)
echo "run-mutants.sh: all $total controls behaved as declared (${#expected_passes[@]} unfalsifiable, $((total - ${#expected_passes[@]})) rejected)."
exit 0
