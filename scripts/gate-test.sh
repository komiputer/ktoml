#!/usr/bin/env bash
# Land-gate test command: mirror drift check, then SnaKt verification (compileKotlinJvm fails on a
# proof failure) and the JVM tests. SNAKT_MAVEN_REPO names the private Maven repository that holds
# the pinned formver build (docs/verification/plugin-pin.md). The compiler runs in-process because
# the host stops Kotlin daemons that live longer than five minutes.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

maven_repo="${SNAKT_MAVEN_REPO:-$HOME/tmp/volatile/snakt-m2}"

# The compliance tests read the toml-test submodule, which a fresh checkout does not have.
git submodule update --init toml-test
scripts/check-mirrors.sh
./gradlew --no-daemon :ktoml-core:compileKotlinJvm :ktoml-core:jvmTest -Dmaven.repo.local="$maven_repo" \
    -Pkotlin.compiler.execution.strategy=in-process "-Dorg.gradle.jvmargs=-Xmx6g -Xss30m -XX:MaxMetaspaceSize=2g"
