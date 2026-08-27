package com.akuleshov7.ktoml.verified

import org.jetbrains.kotlin.formver.plugin.*

/**
 * Phase 1 chunk 1 (plan.md §1.2): the trim family from `parsers/StringUtils.kt`, mirrored per
 * plan.md §4.2 (throw -> sentinel/predicate rewrite; exception-free is not the point here since
 * none of these throw, but `startsWith`/`endsWith` convert without axioms, so per the house rule in
 * plan.md §5 they are hand-spelled below as [hasPrefix]/[hasSuffix] rather than trusted directly).
 * `isLetterOrDigit`, also listed in plan.md's Phase 1 table, already lives in Lexing.kt in this
 * package and is reused as-is.
 *
 * [hasPrefix]/[hasSuffix]/[lastIndexOfChar]/[allEqualFrom] are `while`-loop implementations, so
 * they cannot be marked `@Pure` and cannot be called from inside another function's
 * `preconditions`/`postconditions`/`loopInvariants` block (SnaKt requires spec-block callees to be
 * pure). Every spec below that needs one of these predicates therefore inlines the same `forAll`
 * formula as that function's own postcondition, rather than calling the function — the two are
 * kept textually next to each other so they can be eyeballed for agreement.
 *
 * scripts/check-mirrors.sh (docs/verification/mirrors.txt) fails the build if any mirrored
 * upstream function's source changes since it was mirrored.
 */

/**
 * Hand-spelled `String.startsWith(prefix)` (house rule, plan.md §5): stdlib's version converts
 * but carries no axioms a caller's spec can lean on.
 */
@AlwaysVerify
internal fun hasPrefix(s: String, prefix: String): Boolean {
    postconditions<Boolean> { r ->
        r == (prefix.length <= s.length &&
            forAll<Int> { (0 <= it && it < prefix.length) implies (s[it] == prefix[it]) })
    }
    if (prefix.length > s.length) {
        return false
    }
    var i = 0
    while (i < prefix.length) {
        loopInvariants {
            0 <= i && i <= prefix.length
            forAll<Int> { (0 <= it && it < i) implies (s[it] == prefix[it]) }
        }
        if (s[i] != prefix[i]) {
            return false
        }
        i = i + 1
    }
    return true
}

/**
 * Hand-spelled `String.endsWith(suffix)` (house rule, plan.md §5).
 */
@AlwaysVerify
internal fun hasSuffix(s: String, suffix: String): Boolean {
    postconditions<Boolean> { r ->
        r == (suffix.length <= s.length &&
            forAll<Int> {
                (0 <= it && it < suffix.length) implies
                    (s[s.length - suffix.length + it] == suffix[it])
            })
    }
    if (suffix.length > s.length) {
        return false
    }
    val offset = s.length - suffix.length
    var i = 0
    while (i < suffix.length) {
        loopInvariants {
            0 <= i && i <= suffix.length
            forAll<Int> { (0 <= it && it < i) implies (s[offset + it] == suffix[it]) }
        }
        if (s[offset + i] != suffix[i]) {
            return false
        }
        i = i + 1
    }
    return true
}

/**
 * Last index of [c] in [s], or -1. Hand-spelled `String.lastIndexOf(Char)`, needed by
 * [trimMultilineQuotes].
 */
@AlwaysVerify
internal fun lastIndexOfChar(s: String, c: Char): Int {
    postconditions<Int> { r ->
        r == -1 || (0 <= r && r < s.length && s[r] == c)
        (r != -1) implies forAll<Int> { (r < it && it < s.length) implies (s[it] != c) }
        (r == -1) implies forAll<Int> { (0 <= it && it < s.length) implies (s[it] != c) }
    }
    var i = s.length - 1
    var result = -1
    while (i >= 0) {
        loopInvariants {
            -1 <= i && i < s.length
            result == -1 || (i < result && result < s.length && s[result] == c)
            (result != -1) implies forAll<Int> { (result < it && it < s.length) implies (s[it] != c) }
            (result == -1) implies forAll<Int> { (i < it && it < s.length) implies (s[it] != c) }
        }
        if (result == -1 && s[i] == c) {
            result = i
        }
        i = i - 1
    }
    return result
}

/**
 * True iff every character of [s] in `[from, length)` equals [c]. Needed by [trimMultilineQuotes]
 * for the "closing delimiter is alone on its line" check.
 */
@AlwaysVerify
internal fun allEqualFrom(s: String, from: Int, c: Char): Boolean {
    preconditions {
        0 <= from && from <= s.length
    }
    postconditions<Boolean> { r ->
        r == forAll<Int> { (from <= it && it < s.length) implies (s[it] == c) }
    }
    var i = from
    var ok = true
    while (i < s.length) {
        loopInvariants {
            from <= i && i <= s.length
            ok == forAll<Int> { (from <= it && it < i) implies (s[it] == c) }
        }
        if (s[i] != c) {
            ok = false
        }
        i = i + 1
    }
    return ok
}

/**
 * ktoml: `trimSymbols` (private helper of `trimSingleQuotes`/`trimQuotes`/`trimCurlyBraces`/
 * `trimBrackets`/`trimDoubleBrackets`).
 *
 * If [s] starts with [prefix] and ends with [suffix], returns the slice between them; otherwise
 * returns [s] unchanged. Faithful to upstream's `removePrefix(prefix).removeSuffix(suffix)`
 * chaining: the suffix is re-checked against the *post-prefix-removal* string, so a [prefix]/
 * [suffix] pair that overlaps a short [s] (e.g. `s = "a"`, `prefix = suffix = "a"`) is handled the
 * same (degenerate) way upstream is, which the postcondition below does not characterise further.
 */
@AlwaysVerify
internal fun trimSymbols(s: String, prefix: String, suffix: String): String {
    postconditions<String> { r ->
        (prefix.length <= s.length &&
            forAll<Int> { (0 <= it && it < prefix.length) implies (s[it] == prefix[it]) } &&
            suffix.length <= s.length &&
            forAll<Int> {
                (0 <= it && it < suffix.length) implies
                    (s[s.length - suffix.length + it] == suffix[it])
            } &&
            prefix.length + suffix.length <= s.length) implies
            (r.length == s.length - prefix.length - suffix.length &&
                forAll<Int> { (0 <= it && it < r.length) implies (r[it] == s[it + prefix.length]) })
        (!(prefix.length <= s.length &&
            forAll<Int> { (0 <= it && it < prefix.length) implies (s[it] == prefix[it]) } &&
            suffix.length <= s.length &&
            forAll<Int> {
                (0 <= it && it < suffix.length) implies
                    (s[s.length - suffix.length + it] == suffix[it])
            })) implies (r == s)
    }
    if (hasPrefix(s, prefix) && hasSuffix(s, suffix)) {
        val mid = slice(s, prefix.length, s.length)
        return if (hasSuffix(mid, suffix)) slice(mid, 0, mid.length - suffix.length) else mid
    }
    return s
}

/** ktoml: `String.trimSingleQuotes`. */
@AlwaysVerify
internal fun trimSingleQuotes(s: String): String {
    postconditions<String> { r ->
        (s.length >= 2 && s[0] == '\'' && s[s.length - 1] == '\'') implies
            (r.length == s.length - 2 &&
                forAll<Int> { (0 <= it && it < r.length) implies (r[it] == s[it + 1]) })
        (!(s.length >= 1 && s[0] == '\'' && s[s.length - 1] == '\'')) implies (r == s)
    }
    return trimSymbols(s, "'", "'")
}

/** ktoml: `String.trimQuotes`. */
@AlwaysVerify
internal fun trimQuotes(s: String): String {
    postconditions<String> { r ->
        (s.length >= 2 && s[0] == '"' && s[s.length - 1] == '"') implies
            (r.length == s.length - 2 &&
                forAll<Int> { (0 <= it && it < r.length) implies (r[it] == s[it + 1]) })
        (!(s.length >= 1 && s[0] == '"' && s[s.length - 1] == '"')) implies (r == s)
    }
    return trimSymbols(s, "\"", "\"")
}

/** ktoml: `String.trimCurlyBraces`. */
@AlwaysVerify
internal fun trimCurlyBraces(s: String): String {
    postconditions<String> { r ->
        (s.length >= 2 && s[0] == '{' && s[s.length - 1] == '}') implies
            (r.length == s.length - 2 &&
                forAll<Int> { (0 <= it && it < r.length) implies (r[it] == s[it + 1]) })
        (!(s.length >= 1 && s[0] == '{' && s[s.length - 1] == '}')) implies (r == s)
    }
    return trimSymbols(s, "{", "}")
}

/** ktoml: `String.trimBrackets`. */
@AlwaysVerify
internal fun trimBrackets(s: String): String {
    postconditions<String> { r ->
        (s.length >= 2 && s[0] == '[' && s[s.length - 1] == ']') implies
            (r.length == s.length - 2 &&
                forAll<Int> { (0 <= it && it < r.length) implies (r[it] == s[it + 1]) })
        (!(s.length >= 1 && s[0] == '[' && s[s.length - 1] == ']')) implies (r == s)
    }
    return trimSymbols(s, "[", "]")
}

/** ktoml: `String.trimDoubleBrackets`. */
@AlwaysVerify
internal fun trimDoubleBrackets(s: String): String {
    postconditions<String> { r ->
        (s.length >= 4 && s[0] == '[' && s[1] == '[' &&
            s[s.length - 2] == ']' && s[s.length - 1] == ']') implies
            (r.length == s.length - 4 &&
                forAll<Int> { (0 <= it && it < r.length) implies (r[it] == s[it + 2]) })
        (!(s.length >= 2 && s[0] == '[' && s[1] == '[' &&
            s[s.length - 2] == ']' && s[s.length - 1] == ']')) implies (r == s)
    }
    return trimSymbols(s, "[[", "]]")
}

/**
 * ktoml: `String.isNotQuoted` (private helper of `validateSpaces`).
 *
 * Mirrors upstream exactly: `!(startsWith("\"") && endsWith("\""))`, with no explicit length
 * check. Plan.md §1.2 calls out the consequence: a single `"` character satisfies both the prefix
 * and suffix check against itself, so `isNotQuoted("\"") == false` even though there is no quote
 * *pair* to strip — the length<2 edge is a real property of the upstream predicate, not a rewrite
 * artifact, and is asserted explicitly below rather than silently inherited.
 */
@AlwaysVerify
internal fun isNotQuoted(s: String): Boolean {
    postconditions<Boolean> { r ->
        r == !(s.length >= 1 && s[0] == '"' && s[s.length - 1] == '"')
        (s.length == 1 && s[0] == '"') implies !r
    }
    return !(hasPrefix(s, "\"") && hasSuffix(s, "\""))
}

/**
 * ktoml: the private `String.trimMultilineQuotes(quotes: String)` helper behind
 * `trimMultilineQuotes()`/`trimMultilineLiteralQuotes()`.
 *
 * If [s] is wrapped in [quotes], strips them; if what remains starts with a newline and ends with
 * a line containing only spaces (the closing delimiter was on its own, indented, line), that
 * trailing space-only line is dropped too, keeping the newline before it. Otherwise returns the
 * dequoted content (or [s] itself, if it wasn't wrapped in [quotes]) unchanged.
 *
 * Upstream's variant of this check is `trimmedStr.removePrefix(newLineChar().toString())
 * .substring(lastNewLine).all { it == ' ' }`: removing a leading newline shifts every index by
 * one, so re-using `lastNewLine` (computed on the *original* `trimmedStr`) against the shifted
 * string only lines up with "everything after the last newline is a space" when `trimmedStr`
 * actually starts with a newline; when it does not, the substring always includes the `'\n'`
 * itself at `lastNewLine` and `.all { it == ' ' }` is unconditionally false. That reduction
 * (`startsWithNewLine && allEqualFrom(trimmed, lastNewLine + 1, ' ')`) is what is proved below.
 *
 * Idempotence on already-trimmed input (plan.md §1.2) is the second postcondition clause read at
 * `s := ` the output of a prior call: any string not wrapped in [quotes] — which includes every
 * output of this function whose result doesn't happen to be re-wrapped — maps to itself, so a
 * second application is a no-op. A standalone lemma restating this as `f(f(s)) == f(s)` is not a
 * further theorem: it is exactly this clause applied twice and adds nothing SnaKt needs to check
 * separately.
 */
@AlwaysVerify
internal fun trimMultilineQuotes(s: String, quotes: String): String {
    postconditions<String> { r ->
        r.length <= s.length
        (!(quotes.length <= s.length &&
            forAll<Int> { (0 <= it && it < quotes.length) implies (s[it] == quotes[it]) } &&
            quotes.length <= s.length &&
            forAll<Int> {
                (0 <= it && it < quotes.length) implies
                    (s[s.length - quotes.length + it] == quotes[it])
            })) implies (r == s)
    }
    if (hasPrefix(s, quotes) && hasSuffix(s, quotes) && quotes.length <= s.length) {
        val mid = slice(s, quotes.length, s.length)
        val trimmed = if (hasSuffix(mid, quotes)) slice(mid, 0, mid.length - quotes.length) else mid
        val lastNewLine = lastIndexOfChar(trimmed, '\n')
        val startsWithNewLine = trimmed.length > 0 && trimmed[0] == '\n'
        if (lastNewLine != -1 && startsWithNewLine && allEqualFrom(trimmed, lastNewLine + 1, ' ')) {
            return slice(trimmed, 0, lastNewLine + 1)
        }
        return trimmed
    }
    return s
}
