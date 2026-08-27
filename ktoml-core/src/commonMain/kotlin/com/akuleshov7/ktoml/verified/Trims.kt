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
internal fun trimSymbols(s: String, prefix: String, suffix: String): String {
    postconditions<String> { r ->
        (prefix.length <= s.length &&
            forAll<Int> { (0 <= it && it < prefix.length) implies (s[it] == prefix[it]) } &&
            suffix.length <= s.length &&
            forAll<Int> {
                (0 <= it && it < suffix.length) implies
                    (s[s.length - suffix.length + it] == suffix[it])
            } &&
            prefix.length <= s.length - suffix.length) implies
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

/**
 * Phase 1 chunk 2 (plan.md §1.2): `getCountOfOccurrencesOfSubstring`,
 * `startsWithIgnoreAllWhitespaces`, `convertLineEndingBackslash`, `trimComment` from
 * `parsers/StringUtils.kt`. `isLineEndingBackslash` and `takeBeforeComment`, also listed in the
 * plan's chunk 2 row, were already mirrored into Lexing.kt during Phase 0 (baseline §1.1) — no new
 * code for either, reused as-is below.
 */

/**
 * True iff [sub] occurs in [s] starting exactly at [i]. Helper for
 * [getCountOfOccurrencesOfSubstring]; same shape as [hasPrefix] but at an arbitrary offset.
 */
internal fun matchesAt(s: String, i: Int, sub: String): Boolean {
    preconditions {
        0 <= i && i <= s.length
    }
    postconditions<Boolean> { r ->
        r == (sub.length <= s.length - i &&
            forAll<Int> { (0 <= it && it < sub.length) implies (s[i + it] == sub[it]) })
    }
    if (sub.length > s.length - i) {
        return false
    }
    var k = 0
    while (k < sub.length) {
        loopInvariants {
            0 <= k && k <= sub.length
            forAll<Int> { (0 <= it && it < k) implies (s[i + it] == sub[it]) }
        }
        if (s[i + k] != sub[k]) {
            return false
        }
        k = k + 1
    }
    return true
}

/**
 * ktoml: `String.getCountOfOccurrencesOfSubstring`, rewritten `split`-free as a `while` scan
 * that walks [s] left to right, counting non-overlapping matches of [substring] exactly the way
 * `split(substring).size - 1` does (a match consumes `substring.length` characters before the
 * scan resumes).
 *
 * Proved: total; result non-negative; result bounded by `s.length / substring.length` (plan.md
 * §1.2's third clause). The plan also asks for "0 iff no occurrence"; SnaKt's `forAll` is
 * single-bound-variable only with no `exists` (docs/verification/snakt-capabilities.md), so
 * "no occurrence anywhere in `s`" cannot itself be stated without a quantifier alternation this
 * plugin pin doesn't support. WEAKENED to the direction that *is* expressible without one: a
 * match at the very first position forces a positive count (`matchesAt(s, 0, substring) implies
 * r >= 1`), which is the sentinel-adjacent case a mutant is most likely to break.
 */
internal fun getCountOfOccurrencesOfSubstring(s: String, substring: String): Int {
    preconditions {
        substring.length >= 1
    }
    postconditions<Int> { r ->
        r >= 0
        r.toLong() * substring.length <= s.length
        (substring.length <= s.length &&
            forAll<Int> { (0 <= it && it < substring.length) implies (s[it] == substring[it]) }) implies
            (r >= 1)
        (s.length == 0) implies (r == 0)
    }
    var i = 0
    var count = 0
    while (i <= s.length - substring.length) {
        loopInvariants {
            0 <= i && i <= s.length
            count >= 0
            count <= i
            count.toLong() * substring.length <= i
            (i > 0 && substring.length <= s.length &&
                forAll<Int> { (0 <= it && it < substring.length) implies (s[it] == substring[it]) }) implies
                (count >= 1)
        }
        if (matchesAt(s, i, substring)) {
            count = count + 1
            i = i + substring.length
        } else {
            i = i + 1
        }
    }
    return count
}

/**
 * Hand-spelled `Char.isWhitespace()` (house rule, plan.md §5: a stdlib predicate that converts but
 * carries no axioms). The character set is the one `Char.isWhitespace()` accepts: the Unicode space,
 * line and paragraph separators and the controls U+0009..U+000D and U+001C..U+001F.
 *
 * `@Pure`: the body is branch-free, so this is legal per the `@Pure`-requires-expression-body rule
 * (plan.md friction, chunk 1) -- needed so `verified/Multiline.kt`'s `allWhitespaceBetween` can call
 * it from inside a `forAll` in its own `postconditions`/`loopInvariants` blocks.
 */
@Pure
internal fun isKotlinWhitespace(c: Char): Boolean {
    postconditions<Boolean> { r ->
        r == (('\u0009' <= c && c <= '\u000D') || ('\u001C' <= c && c <= '\u0020') || c == '\u0085' || c == '\u00A0' || c == '\u1680' ||
            ('\u2000' <= c && c <= '\u200A') || c == '\u2028' || c == '\u2029' || c == '\u202F' ||
            c == '\u205F' || c == '\u3000')
    }
    return ('\u0009' <= c && c <= '\u000D') || ('\u001C' <= c && c <= '\u0020') || c == '\u0085' || c == '\u00A0' || c == '\u1680' ||
        ('\u2000' <= c && c <= '\u200A') || c == '\u2028' || c == '\u2029' || c == '\u202F' ||
        c == '\u205F' || c == '\u3000'
}

/**
 * ktoml: `String.startsWithIgnoreAllWhitespaces`, rewritten from `filterNot { it.isWhitespace()
 * }.startsWith(prefix)` as a two-pointer `while` scan: [s] is walked skipping whitespace, [prefix]
 * is walked in lockstep, and the result is true iff every character of [prefix] is matched in
 * order against a non-whitespace character of [s].
 *
 * Proved: total. WEAKENED: a full "agrees with the whitespace-skipping characterisation" postcondition
 * would need to relate the result to an independently-built filtered copy of [s], which itself needs a
 * position-correspondence ("the k-th matched prefix character is [s]'s j-th non-whitespace
 * character") that isn't expressible with SnaKt's single-variable, `exists`-free `forAll` — the
 * same alternation gap noted on [getCountOfOccurrencesOfSubstring] above. The two-pointer
 * implementation below *is* that characterisation, definitionally; no independent restatement of
 * it is provable at this plugin pin.
 */
@Suppress("FUNCTION_BOOLEAN_PREFIX")
internal fun startsWithIgnoreAllWhitespaces(s: String, prefix: String): Boolean {
    var i = 0
    var j = 0
    while (i < s.length && j < prefix.length) {
        loopInvariants {
            0 <= i && i <= s.length
            0 <= j && j <= prefix.length
        }
        if (isKotlinWhitespace(s[i])) {
            i = i + 1
        } else if (s[i] == prefix[j]) {
            i = i + 1
            j = j + 1
        } else {
            return false
        }
    }
    return j == prefix.length
}

/**
 * ktoml: `String.convertLineEndingBackslash`, rewritten index-based (no `.split`). Scans [s] for
 * the first line-ending-backslash sequence `\` immediately followed by `\n`; if none exists,
 * returns [s] unchanged (upstream's `splitEndingBackslash.size == 1` guard). Otherwise splits [s]
 * into segments at every such sequence and rebuilds the string, `trimStart`-ing every segment
 * (including the first), matching upstream's `joinToString("") { it.trimStart() }` exactly.
 *
 * Proved: total; result no longer than input; unchanged when no `\` + `\n` pair occurs. WEAKENED:
 * the plan's "result is a prefix up to the trailing-backslash position" is exact only for a single
 * occurrence — upstream (and this mirror) handles arbitrarily many, splicing segments back
 * together, so the general result is not a literal prefix of [s]. The provable, upstream-faithful
 * property is the bound and the no-op case stated below.
 */
internal fun convertLineEndingBackslash(s: String): String {
    postconditions<String> { r ->
        r.length <= s.length
        (forAll<Int> {
            (0 <= it && it < s.length - 1) implies !(s[it] == '\\' && s[it + 1] == '\n')
        }) implies (r == s)
    }
    var hasMarker = false
    var markerIdx = 0
    var i = 0
    while (i < s.length - 1) {
        loopInvariants {
            0 <= i
            (s.length == 0 && i == 0) || i <= s.length - 1
            !hasMarker implies forAll<Int> {
                (0 <= it && it < i && it + 1 < s.length) implies !(s[it] == '\\' && s[it + 1] == '\n')
            }
            hasMarker implies (0 <= markerIdx && markerIdx < s.length - 1 &&
                s[markerIdx] == '\\' && s[markerIdx + 1] == '\n')
        }
        if (s[i] == '\\' && s[i + 1] == '\n') {
            hasMarker = true
            markerIdx = i
        }
        i = i + 1
    }
    if (!hasMarker) {
        return s
    }
    var res = ""
    var partStart = 0
    var k = 0
    while (k < s.length) {
        loopInvariants {
            0 <= k && k <= s.length
            0 <= partStart && partStart <= k
            res.length <= partStart
        }
        if (k < s.length - 1 && s[k] == '\\' && s[k + 1] == '\n') {
            var trimStart = partStart
            while (trimStart < k && isKotlinWhitespace(s[trimStart])) {
                loopInvariants {
                    partStart <= trimStart && trimStart <= k
                }
                trimStart = trimStart + 1
            }
            res = res + slice(s, trimStart, k)
            k = k + 2
            partStart = k
        } else {
            k = k + 1
        }
    }
    var trimStart = partStart
    while (trimStart < s.length && isKotlinWhitespace(s[trimStart])) {
        loopInvariants {
            partStart <= trimStart && trimStart <= s.length
        }
        trimStart = trimStart + 1
    }
    res = res + slice(s, trimStart, s.length)
    return res
}

/**
 * ktoml: `String.trimComment`, built like `takeBeforeComment` (Lexing.kt) on
 * [indexOfNextOutsideQuotes] and [slice]: everything after the first unquoted `#` (or the whole
 * of an already-comment-free string maps to `""`), with surrounding whitespace trimmed off.
 *
 * Proved: total; result no longer than input. WEAKENED, same as `takeBeforeComment`'s own scope
 * note: "result contains no unquoted #" isn't provable at this postcondition surface, because
 * that requires reasoning about quoting state at every interior index, not just at the single
 * index [indexOfNextOutsideQuotes] returns; would need its own strengthened postcondition (out of
 * scope for this mirror, not attempted).
 */
internal fun trimComment(s: String, allowEscapedQuotesInLiteralStrings: Boolean): String {
    postconditions<String> { r ->
        r.length <= s.length
    }
    val commentStartIndex = indexOfNextOutsideQuotes(s, allowEscapedQuotesInLiteralStrings, '#', 0)
    if (commentStartIndex == -1) {
        return ""
    }
    var lo = commentStartIndex + 1
    while (lo < s.length && isKotlinWhitespace(s[lo])) {
        loopInvariants {
            commentStartIndex + 1 <= lo && lo <= s.length
        }
        lo = lo + 1
    }
    var hi = s.length
    while (hi > lo && isKotlinWhitespace(s[hi - 1])) {
        loopInvariants {
            lo <= hi && hi <= s.length
        }
        hi = hi - 1
    }
    return slice(s, lo, hi)
}
