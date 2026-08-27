package com.akuleshov7.ktoml.verified

import org.jetbrains.kotlin.formver.plugin.*

/**
 * A parsing kernel lifted from ktoml (akuleshov7/ktoml), specified for crash-freedom.
 *
 * Sources: `parsers/StringUtils.kt` and `utils/SpecialCharacters.kt` in this module.
 *
 * This is a MIRROR directory (plan.md §4.2): each function below is an exception-free
 * rewrite of an upstream function that cannot yet carry a spec in place (throw -> sentinel).
 * Every mirrored function's doc comment names its upstream original. scripts/check-mirrors.sh,
 * driven by docs/verification/mirrors.txt, fails the build if any upstream original's source
 * changes since it was mirrored.
 */

// `Char.isWhitespace` converts but carries no axioms, so nothing downstream can be
// proved about a guard that uses it. TOML's own whitespace set is these four.
@AlwaysVerify
internal fun isTomlWhitespace(c: Char): Boolean {
    postconditions<Boolean> { r ->
        r == (c == ' ' || c == '\t' || c == '\r' || c == '\n')
    }
    return c == ' ' || c == '\t' || c == '\r' || c == '\n'
}

/**
 * ktoml: `String.isLineEndingBackslash`.
 *
 * A line-ending backslash is one followed only by whitespace up to a newline or the
 * end of the string.
 */
@AlwaysVerify
internal fun isLineEndingBackslash(s: String, backslashIndex: Int): Boolean {
    preconditions {
        0 <= backslashIndex && backslashIndex < s.length
    }
    var j = backslashIndex + 1
    while (j < s.length && s[j] != '\n' && isTomlWhitespace(s[j])) {
        loopInvariants {
            backslashIndex + 1 <= j && j <= s.length
        }
        j = j + 1
    }
    return j == s.length || s[j] == '\n'
}

/**
 * ktoml: the scanning half of `String.splitKeyToTokens`.
 *
 * Returns the end of the key part starting at [startIndex]: the index of the next
 * dot that is outside quotes, or the end of the string.
 */
@AlwaysVerify
internal fun nextKeyPartEnd(s: String, startIndex: Int): Int {
    preconditions {
        0 <= startIndex && startIndex <= s.length
    }
    postconditions<Int> { r ->
        startIndex <= r && r <= s.length
        (r < s.length) implies (s[r] == '.')
    }
    var singleQuoteIsClosed = true
    var doubleQuoteIsClosed = true
    var i = startIndex
    while (i < s.length) {
        loopInvariants {
            startIndex <= i && i <= s.length
        }
        val ch = s[i]
        if (ch == '\'') {
            singleQuoteIsClosed = !singleQuoteIsClosed
        } else if (ch == '"') {
            doubleQuoteIsClosed = !doubleQuoteIsClosed
        } else if (ch == '.' && singleQuoteIsClosed && doubleQuoteIsClosed) {
            return i
        }
        i = i + 1
    }
    return s.length
}

/**
 * ktoml: the `count { it == c }` inside `String.validateQuotes`.
 */
@AlwaysVerify
internal fun countChar(s: String, c: Char): Int {
    postconditions<Int> { n ->
        0 <= n && n <= s.length
    }
    var n = 0
    var i = 0
    while (i < s.length) {
        loopInvariants {
            0 <= i && i <= s.length
            0 <= n && n <= i
        }
        if (s[i] == c) {
            n = n + 1
        }
        i = i + 1
    }
    return n
}

/**
 * ktoml: `String.validateQuotes`, as a predicate rather than a thrown ParseException.
 */
@AlwaysVerify
internal fun hasBalancedQuotes(s: String): Boolean {
    return countChar(s, '"') % 2 == 0 && countChar(s, '\'') % 2 == 0
}

/**
 * ktoml: `Char.isLetterOrDigit`, the private helper of `String.validateSymbols`.
 */
@AlwaysVerify
@Pure
internal fun isLetterOrDigit(c: Char): Boolean {
    postconditions<Boolean> { r ->
        r == (('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z') || ('0' <= c && c <= '9'))
    }
    return ('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z') || ('0' <= c && c <= '9')
}

/**
 * ktoml: `String.validateSymbols`, as a search rather than a thrown ParseException.
 *
 * Returns the index of the first character of an unquoted key part that TOML does not
 * allow bare, or -1 if there is none.
 */
@AlwaysVerify
internal fun firstInvalidBareKeyChar(s: String): Int {
    postconditions<Int> { r ->
        r == -1 || (0 <= r && r < s.length)
        (r != -1) implies (!isLetterOrDigit(s[r]) &&
            s[r] != '_' && s[r] != '-' && s[r] != '.' && s[r] != ' ' && s[r] != '\t')
    }
    var singleQuoteIsClosed = true
    var doubleQuoteIsClosed = true
    var i = 0
    while (i < s.length) {
        loopInvariants {
            0 <= i && i <= s.length
        }
        val ch = s[i]
        if (ch == '\'') {
            singleQuoteIsClosed = !singleQuoteIsClosed
        } else if (ch == '"') {
            doubleQuoteIsClosed = !doubleQuoteIsClosed
        } else if (doubleQuoteIsClosed && singleQuoteIsClosed &&
            ch != '_' && ch != '-' && ch != '.' && ch != ' ' && ch != '\t' &&
            !isLetterOrDigit(ch)
        ) {
            return i
        }
        i = i + 1
    }
    return -1
}

/**
 * ktoml: the `isEscapingDisabled` computation inside `String.replaceEscaped`.
 *
 * Escaping is off exactly when the config allows escaped quotes in literal strings and
 * the first quote character at or after [startIndex] is a single quote.
 */
@AlwaysVerify
internal fun escapingDisabled(s: String, startIndex: Int, allowEscapedQuotesInLiteralStrings: Boolean): Boolean {
    preconditions {
        0 <= startIndex && startIndex <= s.length
    }
    if (!allowEscapedQuotesInLiteralStrings) {
        return false
    }
    var i = startIndex
    while (i < s.length) {
        loopInvariants {
            startIndex <= i && i <= s.length
        }
        val c = s[i]
        if (c == '"') {
            return false
        }
        if (c == '\'') {
            return true
        }
        i = i + 1
    }
    return false
}

/**
 * ktoml: `String.indexOfNextOutsideQuotes`.
 *
 * Index of the first [searchChar] at or after [startIndex] that is not enclosed in
 * single, double or triple quotes, or -1.
 */
@AlwaysVerify
internal fun indexOfNextOutsideQuotes(
    s: String,
    allowEscapedQuotesInLiteralStrings: Boolean,
    searchChar: Char,
    startIndex: Int
): Int {
    preconditions {
        0 <= startIndex && startIndex <= s.length
    }
    postconditions<Int> { r ->
        r == -1 || (startIndex <= r && r < s.length)
        (r != -1) implies (s[r] == searchChar)
        // On input with no quoting to track, -1 really does mean "not present".
        (forAll<Int> {
            (startIndex <= it && it < s.length) implies
                (s[it] != '"' && s[it] != '\'' && s[it] != '\\')
        } && r == -1) implies forAll<Int> {
            (startIndex <= it && it < s.length) implies (s[it] != searchChar)
        }
    }
    val escapesCollapse = !escapingDisabled(s, startIndex, allowEscapedQuotesInLiteralStrings)
    // ktoml tracks the open delimiter as a nullable String; the pair below is the same
    // state with no allocation: quoteLen == 0 means outside quotes.
    var quoteChar = ' '
    var quoteLen = 0
    var idx = startIndex
    while (idx < s.length) {
        loopInvariants {
            startIndex <= idx && idx <= s.length
            quoteLen == 0 || quoteLen == 1 || quoteLen == 3
            (forAll<Int> {
                (startIndex <= it && it < s.length) implies
                    (s[it] != '"' && s[it] != '\'' && s[it] != '\\')
            }) implies (quoteLen == 0 && forAll<Int> {
                (startIndex <= it && it < idx) implies (s[it] != searchChar)
            })
        }
        val symbol = s[idx]
        if (escapesCollapse && symbol == '\\' && idx + 1 < s.length &&
            (s[idx + 1] == '"' || s[idx + 1] == '\'')
        ) {
            idx = idx + 2
        } else if (quoteLen == 0 && symbol == searchChar) {
            return idx
        } else if (symbol != '"' && symbol != '\'') {
            idx = idx + 1
        } else if (quoteLen == 0) {
            quoteChar = symbol
            if (idx + 2 < s.length && s[idx + 1] == symbol && s[idx + 2] == symbol) {
                quoteLen = 3
                idx = idx + 3
            } else {
                quoteLen = 1
                idx = idx + 1
            }
        } else if (quoteChar == symbol && quoteLen == 1) {
            quoteLen = 0
            idx = idx + 1
        } else if (quoteChar == symbol && quoteLen == 3 && idx + 3 <= s.length &&
            s[idx + 1] == symbol && s[idx + 2] == symbol
        ) {
            quoteLen = 0
            idx = idx + 3
        } else {
            idx = idx + 1
        }
    }
    return -1
}

/**
 * ktoml: the digit half of `hexCode.toInt(HEX_RADIX)` in `appendEscapedUnicode`.
 *
 * Returns -1 for a character that is not a hex digit, where ktoml's `toInt` throws.
 */
@AlwaysVerify
internal fun hexDigitValue(c: Char): Int {
    postconditions<Int> { v ->
        -1 <= v && v <= 15
    }
    if ('0' <= c && c <= '9') {
        return c - '0'
    }
    if ('a' <= c && c <= 'f') {
        return c - 'a' + 10
    }
    if ('A' <= c && c <= 'F') {
        return c - 'A' + 10
    }
    return -1
}

/**
 * ktoml: the escape walk of `String.convertSpecialCharacters` together with the length
 * check of `StringBuilder.appendEscapedUnicode`.
 *
 * Returns the index of the backslash opening the first malformed escape sequence, or -1
 * when every escape in [s] is well formed. ktoml throws UnknownEscapeSymbolsException at
 * that position instead.
 */
@AlwaysVerify
internal fun firstBadEscape(s: String): Int {
    postconditions<Int> { r ->
        r == -1 || (0 <= r && r < s.length)
        (r != -1) implies (s[r] == '\\')
    }
    var i = 0
    while (i < s.length) {
        loopInvariants {
            0 <= i && i <= s.length
        }
        val currentChar = s[i]
        var offset = 1
        if (currentChar == '\\' && i != s.length - 1) {
            val next = s[i + 1]
            offset = 2
            if (next == 't' || next == 'b' || next == 'r' || next == 'n' || next == 'f' ||
                next == '\\' || next == '\'' || next == '"'
            ) {
                offset = 2
            } else if (next == 'u' || next == 'U') {
                val nbUnicodeChars = if (next == 'u') 4 else 8
                val codeStartIndex = i + 2
                if (codeStartIndex + nbUnicodeChars > s.length) {
                    return i
                }
                var k = 0
                while (k < nbUnicodeChars) {
                    loopInvariants {
                        0 <= k && k <= nbUnicodeChars
                        codeStartIndex + nbUnicodeChars <= s.length
                        0 <= codeStartIndex
                    }
                    if (hexDigitValue(s[codeStartIndex + k]) < 0) {
                        return i
                    }
                    k = k + 1
                }
                offset = 2 + nbUnicodeChars
            } else {
                return i
            }
        }
        i = i + offset
    }
    return -1
}

/**
 * SnaKt models `String.substring` only as an opaque string, so the slice used below is
 * spelled out. Same shape as the `subs` helper in SnaKt's own string algorithm tests.
 */
@AlwaysVerify
internal fun slice(s: String, lo: Int, hi: Int): String {
    preconditions {
        0 <= lo && lo <= hi && hi <= s.length
    }
    postconditions<String> { res ->
        res.length == hi - lo
        forAll<Int> {
            (0 <= it && it < res.length) implies (res[it] == s[it + lo])
        }
    }
    var res = ""
    var i = lo
    while (i < hi) {
        loopInvariants {
            lo <= i && i <= hi
            res.length == i - lo
            forAll<Int> {
                (0 <= it && it < res.length) implies (res[it] == s[it + lo])
            }
        }
        res = res + s[i]
        i = i + 1
    }
    return res
}

/**
 * ktoml: `String.takeBeforeComment`.
 *
 * Present to check that the postcondition of [indexOfNextOutsideQuotes] is by itself
 * enough to discharge the caller's slice bounds; ktoml relies on the same reasoning
 * without stating it.
 */
@AlwaysVerify
internal fun takeBeforeComment(s: String, allowEscapedQuotesInLiteralStrings: Boolean): String {
    postconditions<String> { res ->
        res.length <= s.length
    }
    val commentStartIndex = indexOfNextOutsideQuotes(s, allowEscapedQuotesInLiteralStrings, '#', 0)
    if (commentStartIndex == -1) {
        return s
    }
    return slice(s, 0, commentStartIndex)
}
