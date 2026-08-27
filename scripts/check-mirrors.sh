#!/usr/bin/env bash
# Verifies that upstream functions mirrored under ktoml-core's verified/ source set
# have not drifted since their mirror was written. Manifest: docs/verification/mirrors.txt,
# lines "mirror_function upstream_file upstream_function expected_sha256" (# comments/blank
# lines ignored). Fails loudly on any hash mismatch or missing function.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
manifest="$repo_root/docs/verification/mirrors.txt"

extract_function() {
    local file="$1" fn="$2"
    awk -v fn="$fn" '
    function braces(str, ch,    n, i, c) {
        n = 0
        for (i = 1; i <= length(str); i++) {
            c = substr(str, i, 1)
            if (c == ch) n++
        }
        return n
    }
    BEGIN { started = 0; depth = 0; opened = 0 }
    {
        line = $0
        if (!started) {
            if (line ~ ("^(internal |private |public )?fun ([A-Za-z0-9_.]+\\.)?" fn "\\(")) {
                started = 1
            } else {
                next
            }
        } else if (opened == 0 && line ~ /^(internal |private |public )?fun /) {
            # expression-bodied function ended before another declaration began
            exit
        }
        print line
        o = braces(line, "{")
        c = braces(line, "}")
        depth += o - c
        if (o > 0) opened = 1
        if (opened == 1 && depth <= 0) exit
    }
    ' "$file"
}

fail=0
lineno=0
while IFS= read -r entry || [ -n "$entry" ]; do
    lineno=$((lineno + 1))
    entry="${entry%%#*}"
    entry="$(echo "$entry" | xargs)"
    [ -z "$entry" ] && continue

    read -r mirror_fn upstream_file upstream_fn expected_sha <<<"$entry"
    upstream_path="$repo_root/$upstream_file"

    if [ ! -f "$upstream_path" ]; then
        echo "check-mirrors: $upstream_file (mirror of $mirror_fn) no longer exists" >&2
        fail=1
        continue
    fi

    body="$(extract_function "$upstream_path" "$upstream_fn")"
    if [ -z "$body" ]; then
        echo "check-mirrors: could not find upstream function '$upstream_fn' in $upstream_file (mirror of $mirror_fn)" >&2
        fail=1
        continue
    fi

    actual_sha="$(printf '%s' "$body" | sha256sum | cut -d' ' -f1)"
    if [ "$actual_sha" != "$expected_sha" ]; then
        echo "check-mirrors: '$upstream_fn' in $upstream_file has drifted since '$mirror_fn' was mirrored (docs/verification/mirrors.txt:$lineno)" >&2
        echo "  expected sha256 $expected_sha, got $actual_sha" >&2
        fail=1
    fi
done <"$manifest"

if [ "$fail" -ne 0 ]; then
    echo "check-mirrors: FAILED — revisit the drifted mirror(s) in ktoml-core/src/commonMain/kotlin/com/akuleshov7/ktoml/verified/Lexing.kt" >&2
    exit 1
fi

echo "check-mirrors: all mirrored upstream functions match their recorded hash"
