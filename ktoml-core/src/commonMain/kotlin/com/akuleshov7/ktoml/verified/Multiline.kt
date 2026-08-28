package com.akuleshov7.ktoml.verified

import org.jetbrains.kotlin.formver.plugin.*

/**
 * Phase 1 chunk 4 (plan.md §1.2, last spec chunk): the `TomlMultilineString` triple-quote/nesting
 * family, mirrored per plan.md §4.2. `indexOfCharFrom` is a new helper standing in for
 * `String.indexOf(Char, Int)` (house rule, plan.md §5: a stdlib predicate that converts but
 * carries no axioms) and carries no manifest entry.
 *
 * scripts/check-mirrors.sh (docs/verification/mirrors.txt) fails the build if any mirrored
 * upstream function's source changes since it was mirrored.
 */

/**
 * ktoml: the private `String.isNextThreeQuotes` helper of `TomlMultilineString.trackMultilineString`.
 *
 * Upstream calls this only with `index` ranging over `0..line.length - 3`, so `index + 2` never
 * exceeds `line.length - 1` at the real call site -- but the function itself has no such guard, and
 * its `line[index]`/`line[index + 1]`/`line[index + 2]` indexing is exactly the class of bug found
 * in `isLineEndingBackslash` (index arithmetic trusted without a bound). Mirrored with the
 * precondition spelled out explicitly so SnaKt proves the indexing itself is safe, not merely that
 * upstream happens to call it safely today.
 */
@AlwaysVerify
@Pure
internal fun isNextThreeQuotes(line: String, index: Int, quote: Char): Boolean {
    preconditions {
        0 <= index && index + 2 < line.length
    }
    postconditions<Boolean> { r ->
        r == (line[index] == quote && line[index + 1] == quote && line[index + 2] == quote)
    }
    return line[index] == quote && line[index + 1] == quote && line[index + 2] == quote
}

/**
 * Hand-spelled `String.indexOf(Char, Int)` (house rule, plan.md §5): stdlib's version converts but
 * carries no axioms a caller's spec can lean on. Needed by
 * [hasTwoConsecutiveSymbolsIgnoreWhitespaces].
 */
@AlwaysVerify
internal fun indexOfCharFrom(s: String, c: Char, from: Int): Int {
    preconditions {
        0 <= from && from <= s.length
    }
    postconditions<Int> { r ->
        r == -1 || (from <= r && r < s.length && s[r] == c)
        (r != -1) implies forAll<Int> { (from <= it && it < r) implies (s[it] != c) }
        (r == -1) implies forAll<Int> { (from <= it && it < s.length) implies (s[it] != c) }
    }
    var i = from
    while (i < s.length) {
        loopInvariants {
            from <= i && i <= s.length
            forAll<Int> { (from <= it && it < i) implies (s[it] != c) }
        }
        if (s[i] == c) {
            return i
        }
        i = i + 1
    }
    return -1
}

/**
 * ktoml: the private `TomlMultilineString.hasTwoConsecutiveSymbolsIgnoreWhitespaces`. Total,
 * `Boolean?`-returning: finds the first occurrence of [searchSymbol] in [value]; if a second
 * occurrence follows, returns whether everything strictly between the two is whitespace; if no
 * second occurrence exists, returns `null` when the tail after the first occurrence is all
 * whitespace (still-open, undecided) and `false` when it contains a non-whitespace character
 * (decided: not nested). `null` is returned in exactly one case -- the "found one symbol, nothing
 * but whitespace after it, no second symbol yet" case -- which is the distinction
 * `isEndOfMultilineValue` acts on (it only recomputes `isNested` while it stays `null`). Nullable-
 * flow showcase per plan.md §1.2.
 *
 * WEAKENED: the exact "`null` iff a first occurrence exists with nothing but whitespace after it
 * and no second occurrence" characterization needs an existential witness (the first occurrence's
 * index) that SnaKt's single-variable, `exists`-free `forAll` cannot state directly -- same
 * alternation gap as `getCountOfOccurrencesOfSubstring`/`startsWithIgnoreAllWhitespaces` (Phase 1
 * chunk 2 friction). Compounding that: calling [indexOfCharFrom] (a `while`-loop function, so not
 * `@Pure`) from inside a `postconditions` block at all crashes the plugin with the same internal
 * error documented for `hasPrefix`/`hasSuffix` in Phase 1 chunk 1's friction entry, so the full
 * characterization can't even be spelled out in terms of it. Proved instead: the one direction that
 * *is* `forAll`-expressible without calling another function from the spec block -- if
 * [searchSymbol] does not occur in [value] at all, the result is `false`, never `null` or `true`
 * (logged in friction.md).
 */
@AlwaysVerify
internal fun hasTwoConsecutiveSymbolsIgnoreWhitespaces(value: String, searchSymbol: Char): Boolean? {
    postconditions<Boolean?> { r ->
        (forAll<Int> { (0 <= it && it < value.length) implies (value[it] != searchSymbol) }) implies
            (r == false)
    }
    val firstIndex = indexOfCharFrom(value, searchSymbol, 0)
    if (firstIndex == -1) {
        return false
    }
    val nextIndex = indexOfCharFrom(value, searchSymbol, firstIndex + 1)
    if (nextIndex != -1) {
        return allWhitespaceBetween(value, firstIndex + 1, nextIndex)
    }
    val restAllWhitespace = allWhitespaceBetween(value, firstIndex + 1, value.length)
    return if (restAllWhitespace) {
        null
    } else {
        false
    }
}

/**
 * True iff every character of [s] in `[from, to)` is ASCII whitespace ([isAsciiWhitespace]).
 * Hand-spelled stand-in for the `.all { it.isWhitespace() }` calls inside
 * `hasTwoConsecutiveSymbolsIgnoreWhitespaces`. WEAKENED scope note, same as [isAsciiWhitespace]
 * itself: narrower than `Char.isWhitespace()`'s full Unicode set (docs/verification/friction.md).
 */
@AlwaysVerify
internal fun allWhitespaceBetween(s: String, from: Int, to: Int): Boolean {
    preconditions {
        0 <= from && from <= to && to <= s.length
    }
    postconditions<Boolean> { r ->
        r == forAll<Int> { (from <= it && it < to) implies isAsciiWhitespace(s[it]) }
    }
    var i = from
    var ok = true
    while (i < to) {
        loopInvariants {
            from <= i && i <= to
            ok == forAll<Int> { (from <= it && it < i) implies isAsciiWhitespace(s[it]) }
        }
        if (!isAsciiWhitespace(s[i])) {
            ok = false
        }
        i = i + 1
    }
    return ok
}
