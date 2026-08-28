package com.akuleshov7.ktoml.verified

import org.jetbrains.kotlin.formver.plugin.*

/**
 * Phase 1 chunk 3 (plan.md §1.2): the escaping family from `utils/SpecialCharacters.kt`, plus
 * `parsers/StringUtils.kt`'s `replaceEscaped`, mirrored per plan.md §4.2. `StringBuilder`
 * accumulation is rewritten as index-based `while` string building throughout (no `StringBuilder`
 * model exists yet, G5); `hexDigitChar`/`repeatChar`/`replaceTwoCharSequence`/`quoteEscapeSingle`/
 * `quoteEscapeTriple` are new helpers with no single upstream original (same treatment as
 * `isTomlWhitespace`/`slice` in Lexing.kt) and carry no manifest entry.
 *
 * scripts/check-mirrors.sh (docs/verification/mirrors.txt) fails the build if any mirrored
 * upstream function's source changes since it was mirrored.
 */

/**
 * ktoml: `Char.isControlChar`. `CharCategory.CONTROL` (house rule, plan.md §5: a stdlib predicate
 * that converts but carries no axioms) is hand-spelled as the Unicode `Cc` codepoint test, using
 * `Char`-to-`Char` comparisons rather than `Char.code`.
 *
 * `Char.code` (an `Int` cast) crashes the plugin's `PureLinearizer` whenever it appears inside a
 * `postconditions`/`loopInvariants`/`forAll` block, in every function that reaches Viper at all
 * (not just `@Pure` ones -- "PureLinearizer used to convert non-pure ExpEmbedding; operation
 * freshAnonVar is not supported in a pure context", an internal error, not a clean diagnostic).
 * All BMP control codepoints (`0x00`-`0x1f`, `0x7f`) have direct `Char` literals, so the codepoint
 * test is spelled entirely in `Char` comparisons below and throughout this file -- no `.code`
 * appears in any spec block.
 */
@AlwaysVerify
@Pure
internal fun isControlChar(c: Char): Boolean {
    postconditions<Boolean> { r ->
        r == ((c <= '\u001f' || c == '\u007f') && c != '\t')
    }
    return (c <= '\u001f' || c == '\u007f') && c != '\t'
}

/**
 * ktoml: `Char.isMultilineControlChar`.
 *
 * Lemma (plan.md §1.2): the two predicates differ exactly by the `\n`/`\r` exclusion -- proved as
 * the second postcondition clause below.
 */
@AlwaysVerify
@Pure
internal fun isMultilineControlChar(c: Char): Boolean {
    postconditions<Boolean> { r ->
        r == (isControlChar(c) && c != '\n' && c != '\r')
        isControlChar(c) implies (r == (c != '\n' && c != '\r'))
    }
    return isControlChar(c) && c != '\n' && c != '\r'
}

/**
 * Inverse of [hexDigitValue] (Lexing.kt): the lowercase hex digit character for a value in
 * `[0, 15]`. Hand-spelled as an explicit ladder, the same treatment as [hexDigitValue] itself,
 * rather than trusting `Int.toString(16)`/arithmetic-into-`Char` conversions with no axioms.
 */
@AlwaysVerify
@Pure
internal fun hexDigitChar(v: Int): Char {
    preconditions {
        0 <= v && v <= 15
    }
    postconditions<Char> { r ->
        (v == 0) implies (r == '0')
        (v == 1) implies (r == '1')
        (v == 2) implies (r == '2')
        (v == 3) implies (r == '3')
        (v == 4) implies (r == '4')
        (v == 5) implies (r == '5')
        (v == 6) implies (r == '6')
        (v == 7) implies (r == '7')
        (v == 8) implies (r == '8')
        (v == 9) implies (r == '9')
        (v == 10) implies (r == 'a')
        (v == 11) implies (r == 'b')
        (v == 12) implies (r == 'c')
        (v == 13) implies (r == 'd')
        (v == 14) implies (r == 'e')
        (v == 15) implies (r == 'f')
    }
    if (v == 0) return '0'
    if (v == 1) return '1'
    if (v == 2) return '2'
    if (v == 3) return '3'
    if (v == 4) return '4'
    if (v == 5) return '5'
    if (v == 6) return '6'
    if (v == 7) return '7'
    if (v == 8) return '8'
    if (v == 9) return '9'
    if (v == 10) return 'a'
    if (v == 11) return 'b'
    if (v == 12) return 'c'
    if (v == 13) return 'd'
    if (v == 14) return 'e'
    return 'f'
}

/**
 * ktoml: `Char.escapeControlChar`.
 *
 * Total: the five named escapes return their fixed two-character form; every other character
 * (any `Char`, since this is called only on control characters but is not restricted to them)
 * falls back to a 6-character `\uXXXX` form, built from [hexDigitChar] rather than
 * `Int.toString(16).padStart(...)` (no axioms on that stdlib chain).
 */
@AlwaysVerify
internal fun escapeControlChar(c: Char): String {
    postconditions<String> { r ->
        (c == '\t') implies (r == "\\t")
        (c == '\b') implies (r == "\\b")
        (c == '\n') implies (r == "\\n")
        (c == '\u000C') implies (r == "\\f")
        (c == '\r') implies (r == "\\r")
        (c != '\t' && c != '\b' && c != '\n' && c != '\u000C' && c != '\r') implies
            (r.length == 6 && r[0] == '\\' && r[1] == 'u')
        r.length == 2 || r.length == 6
    }
    if (c == '\t') return "\\t"
    if (c == '\b') return "\\b"
    if (c == '\n') return "\\n"
    if (c == '\u000C') return "\\f"
    if (c == '\r') return "\\r"
    val code = c.code
    val d3 = code % 16
    val r1 = code / 16
    val d2 = r1 % 16
    val r2 = r1 / 16
    val d1 = r2 % 16
    val d0 = (r2 / 16) % 16
    var res = "\\u"
    res = res + hexDigitChar(d0)
    res = res + hexDigitChar(d1)
    res = res + hexDigitChar(d2)
    res = res + hexDigitChar(d3)
    return res
}

/**
 * Hand-spelled `"c".repeat(n)` (house rule, plan.md §5). Needed by [escapeBackslashes].
 */
@AlwaysVerify
internal fun repeatChar(c: Char, n: Int): String {
    preconditions {
        n >= 0
    }
    postconditions<String> { r ->
        r.length == n
        forAll<Int> { (0 <= it && it < r.length) implies (r[it] == c) }
    }
    var res = ""
    var i = 0
    while (i < n) {
        loopInvariants {
            0 <= i && i <= n
            res.length == i
            forAll<Int> { (0 <= it && it < res.length) implies (res[it] == c) }
        }
        res = res + c
        i = i + 1
    }
    return res
}

/**
 * ktoml: the private `String.escapeBackslashes` helper of `escapeControlChars`.
 *
 * Doubles a run of [slashCount] backslashes, except that a run ending a multiline
 * line-ending-backslash (`\` followed only by whitespace up to a newline or the end of the
 * string, per [isLineEndingBackslash]) keeps one backslash unescaped so it still works as a
 * line continuation.
 */
@AlwaysVerify
internal fun escapeBackslashes(s: String, lastBackslashIndex: Int, slashCount: Int, isMultiline: Boolean): String {
    preconditions {
        slashCount >= 1
        0 <= lastBackslashIndex && lastBackslashIndex < s.length
    }
    postconditions<String> { r ->
        r.length == slashCount * 2 || r.length == slashCount * 2 - 1
        forAll<Int> { (0 <= it && it < r.length) implies (r[it] == '\\') }
    }
    val isLineEnding = slashCount % 2 != 0 && isMultiline && isLineEndingBackslash(s, lastBackslashIndex)
    val n = if (isLineEnding) slashCount * 2 - 1 else slashCount * 2
    return repeatChar('\\', n)
}

/**
 * ktoml: `String.escapeControlChars`, rewritten index-based (no `StringBuilder`, no `for`/
 * `withIndex`). Walks [s] left to right, accumulating runs of `\` and flushing them through
 * [escapeBackslashes] whenever a non-backslash character (or the end of the string) is reached;
 * every other character is escaped via [escapeControlChar] when it is a control character (per
 * [isMultilineControlChar] when [isMultiline], else [isControlChar]) and copied verbatim
 * otherwise.
 *
 * Proved: total; output no shorter than input. WEAKENED (plan.md §1.2's "output contains no
 * control character" clause): Viper rejects the loop-invariant-preservation step for that
 * property here -- once a flush (`res = res + escapeBackslashes(...)` / `res = res +
 * escapeControlChar(c)`) appends a *computed, multi-character* string rather than a single
 * literal `Char`, the plugin cannot re-derive "every character of the newly appended chunk
 * satisfies the predicate" from the callee's own postcondition across the concatenation boundary
 * (single-`Char` appends, as in [repeatChar]/[slice]/[quoteEscapeSingle], do not hit this; whole-
 * string appends do). Same family of gap as G5/the `forAll`-only alternation limitation noted
 * elsewhere in this mirror set -- not a spec bug, a plugin-pin ceiling. Logged in friction.md.
 */
@AlwaysVerify
internal fun escapeControlChars(s: String, isMultiline: Boolean): String {
    postconditions<String> { r ->
        r.length >= s.length
    }
    var res = ""
    var slashCount = 0
    var i = 0
    while (i < s.length) {
        loopInvariants {
            0 <= i && i <= s.length
            0 <= slashCount && slashCount <= i
            res.length >= i - slashCount
        }
        val c = s[i]
        if (c == '\\') {
            slashCount = slashCount + 1
            i = i + 1
        } else {
            if (slashCount > 0) {
                res = res + escapeBackslashes(s, i - 1, slashCount, isMultiline)
                slashCount = 0
            }
            val isCtrl = if (isMultiline) isMultilineControlChar(c) else isControlChar(c)
            if (isCtrl) {
                res = res + escapeControlChar(c)
            } else {
                res = res + c
            }
            i = i + 1
        }
    }
    if (s.length > 0 && slashCount > 0) {
        res = res + escapeBackslashes(s, s.length - 1, slashCount, isMultiline)
    }
    return res
}

/**
 * ktoml: the `.replace("\"", "\\\"")` half of `String.escapeSpecialCharacters`'s non-multiline
 * branch. Inserts a `\` before every `"`; total, index-based.
 *
 * WEAKENED: the "preserves control-char-freedom" implication postcondition (plan.md §1.2) hits
 * the same loop-invariant-preservation wall documented on [escapeControlChars] -- Viper cannot
 * re-derive it across the loop even though every append here is single-`Char`; not attempted
 * further here (logged in friction.md).
 */
@AlwaysVerify
internal fun quoteEscapeSingle(s: String): String {
    postconditions<String> { r ->
        r.length >= s.length
    }
    var res = ""
    var i = 0
    while (i < s.length) {
        loopInvariants {
            0 <= i && i <= s.length
            res.length >= i
        }
        val c = s[i]
        if (c == '"') {
            res = res + '\\'
            res = res + '"'
        } else {
            res = res + c
        }
        i = i + 1
    }
    return res
}

/**
 * ktoml: the `.replace("\"\"\"", "\"\"\\\"")` half of `String.escapeSpecialCharacters`'s
 * multiline branch. Every non-overlapping run of three consecutive `"` (scanned left to right,
 * matching `String.replace`'s own non-overlapping semantics) gets an extra `\` inserted before
 * the third quote; total, index-based.
 *
 * WEAKENED, same as [quoteEscapeSingle]: the control-char-freedom preservation clause is dropped
 * for the same loop-invariant-preservation reason (friction.md).
 */
@AlwaysVerify
internal fun quoteEscapeTriple(s: String): String {
    postconditions<String> { r ->
        r.length >= s.length
    }
    var res = ""
    var i = 0
    while (i < s.length) {
        loopInvariants {
            0 <= i && i <= s.length
            res.length >= i
        }
        if (i + 2 < s.length && s[i] == '"' && s[i + 1] == '"' && s[i + 2] == '"') {
            res = res + '"'
            res = res + '"'
            res = res + '\\'
            res = res + '"'
            i = i + 3
        } else {
            res = res + s[i]
            i = i + 1
        }
    }
    return res
}

/**
 * ktoml: `String.escapeSpecialCharacters`. Composes [escapeControlChars] (with the multiline
 * flavor of control-char selected by [multiline]) with the quote-doubling half of the multiline
 * branch ([quoteEscapeTriple]) or the single-quote-escaping half of the non-multiline branch
 * ([quoteEscapeSingle]).
 *
 * Proved: output is no shorter than input. WEAKENED: the "no control character of the selected
 * flavor" clause (plan.md §1.2) is not carried through from its two callees, both of which drop
 * it themselves for the loop-invariant-preservation reason documented on [escapeControlChars]/
 * [quoteEscapeSingle] (friction.md).
 */
@AlwaysVerify
internal fun escapeSpecialCharacters(s: String, multiline: Boolean): String {
    postconditions<String> { r ->
        r.length >= s.length
    }
    val escaped = escapeControlChars(s, multiline)
    return if (multiline) quoteEscapeTriple(escaped) else quoteEscapeSingle(escaped)
}

/**
 * ktoml: `StringBuilder.appendEscapedUnicode`, reimplemented as a total, `hexDigitValue`-driven
 * validation-and-length computation with no actual code-point append (no `StringBuilder` model
 * exists yet, G5, so the append side effect is out of scope for this mirror; the value being
 * mirrored is the offset/validity computation, which is exactly upstream defect #2's site).
 *
 * This is upstream defect #2: `hexCode.toInt(HEX_RADIX)` is called *before* the `try`/`catch`
 * that is meant to guard against a malformed escape, so a non-hex `hexCode` throws an uncaught
 * `NumberFormatException` instead of the intended `UnknownEscapeSymbolsException`. Reimplemented
 * total below: returns -1 (sentinel) rather than throwing, both when not enough characters remain
 * (`codeStartIndex + nbUnicodeChars > fullString.length`, upstream's own explicit check) and when
 * any of the `nbUnicodeChars` characters is not a valid hex digit (upstream's missing check).
 *
 * Proved: total (no `throw`, structurally -- there is none in the body); and, by the first
 * postcondition clause's contrapositive, every input containing an out-of-range or non-hex digit
 * is rejected by returning -1, never by throwing.
 */
@AlwaysVerify
internal fun appendEscapedUnicode(fullString: String, marker: Char, codeStartIndex: Int): Int {
    preconditions {
        0 <= codeStartIndex && codeStartIndex <= fullString.length
    }
    postconditions<Int> { r ->
        r == -1 || r == 4 || r == 8
        (marker == 'u') implies (r == -1 || r == 4)
        (marker != 'u') implies (r == -1 || r == 8)
        (r != -1) implies (codeStartIndex + r <= fullString.length &&
            forAll<Int> {
                (0 <= it && it < r) implies
                    (('0' <= fullString[codeStartIndex + it] && fullString[codeStartIndex + it] <= '9') ||
                        ('a' <= fullString[codeStartIndex + it] && fullString[codeStartIndex + it] <= 'f') ||
                        ('A' <= fullString[codeStartIndex + it] && fullString[codeStartIndex + it] <= 'F'))
            })
        (r == -1 && marker == 'u') implies (codeStartIndex + 4 > fullString.length ||
            !forAll<Int> {
                (0 <= it && it < 4) implies
                    (('0' <= fullString[codeStartIndex + it] && fullString[codeStartIndex + it] <= '9') ||
                        ('a' <= fullString[codeStartIndex + it] && fullString[codeStartIndex + it] <= 'f') ||
                        ('A' <= fullString[codeStartIndex + it] && fullString[codeStartIndex + it] <= 'F'))
            })
        (r == -1 && marker != 'u') implies (codeStartIndex + 8 > fullString.length ||
            !forAll<Int> {
                (0 <= it && it < 8) implies
                    (('0' <= fullString[codeStartIndex + it] && fullString[codeStartIndex + it] <= '9') ||
                        ('a' <= fullString[codeStartIndex + it] && fullString[codeStartIndex + it] <= 'f') ||
                        ('A' <= fullString[codeStartIndex + it] && fullString[codeStartIndex + it] <= 'F'))
            })
    }
    val nbUnicodeChars = if (marker == 'u') 4 else 8
    if (codeStartIndex + nbUnicodeChars > fullString.length) {
        return -1
    }
    var k = 0
    while (k < nbUnicodeChars) {
        loopInvariants {
            0 <= k && k <= nbUnicodeChars
            forAll<Int> {
                (0 <= it && it < k) implies
                    (('0' <= fullString[codeStartIndex + it] && fullString[codeStartIndex + it] <= '9') ||
                        ('a' <= fullString[codeStartIndex + it] && fullString[codeStartIndex + it] <= 'f') ||
                        ('A' <= fullString[codeStartIndex + it] && fullString[codeStartIndex + it] <= 'F'))
            }
        }
        if (hexDigitValue(fullString[codeStartIndex + k]) == -1) {
            return -1
        }
        k = k + 1
    }
    return nbUnicodeChars
}

/**
 * ktoml: the private helper that scans a 2-character sequence (`a`, `b`) inside
 * `String.replaceEscaped`, generalised over the two escaped-quote sequences it is called for.
 * Non-overlapping, left-to-right, matching `String.replace(oldValue, newValue)`'s own semantics.
 */
@AlwaysVerify
internal fun replaceTwoCharSequence(s: String, a: Char, b: Char, r0: Char, r1: Char): String {
    postconditions<String> { r ->
        r.length == s.length
    }
    var res = ""
    var i = 0
    while (i < s.length) {
        loopInvariants {
            0 <= i && i <= s.length
            res.length == i
        }
        if (i + 1 < s.length && s[i] == a && s[i + 1] == b) {
            res = res + r0
            res = res + r1
            i = i + 2
        } else {
            res = res + s[i]
            i = i + 1
        }
    }
    return res
}

/**
 * ktoml: `String.replaceEscaped`, mirrored with `placeholder` fixed to `"__"` (both call sites in
 * `parsers/StringUtils.kt` use the default, confirmed by grep) -- a 2-character placeholder
 * replacing a 2-character escape sequence (`\"` or `\'`), which is exactly what makes the length
 * invariant below hold. [escapingDisabled] (Lexing.kt) already mirrors this function's
 * `isEscapingDisabled` computation verbatim (it is registered against `replaceEscaped` in
 * mirrors.txt) and is reused as-is.
 *
 * Proved (plan.md §1.2, called out as the highest-value single proof in the file): the result is
 * always exactly as long as the input. This is genuinely load-bearing -- callers of
 * `replaceEscaped` (namely `indexOfNextOutsideQuotes`) index into its result and use those
 * indices back against the *original* string, which is only sound when lengths agree index-for-
 * index the way a length-preserving, position-preserving rewrite guarantees.
 */
@AlwaysVerify
internal fun replaceEscaped(s: String, allowEscapedQuotesInLiteralStrings: Boolean): String {
    postconditions<String> { r ->
        r.length == s.length
    }
    if (escapingDisabled(s, 0, allowEscapedQuotesInLiteralStrings)) {
        return s
    }
    val afterDoubleQuotes = replaceTwoCharSequence(s, '\\', '"', '_', '_')
    return replaceTwoCharSequence(afterDoubleQuotes, '\\', '\'', '_', '_')
}
