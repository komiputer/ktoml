/**
 * Specific implementation for utilities
 */

package com.akuleshov7.ktoml.utils

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

internal actual fun @Unique @Borrowed StringBuilder.appendCodePointCompat(codePoint: Int) {
    // Appends char by char, not through appendCodePoint: formver knows `append(Char)` keeps the receiver
    // borrowed, but treats an unspecified Java method as consuming it. The range checks are spelled out
    // (`Character.isValidCodePoint`, `Character.isBmpCodePoint`) so formver can prove `toChar` exact.
    if (codePoint < 0 || codePoint > 0x10FFFF) {
        throw IllegalArgumentException()
    }
    if (codePoint < 0x10000) {
        append(codePoint.toChar())
    } else {
        append(Character.highSurrogate(codePoint))
        append(Character.lowSurrogate(codePoint))
    }
}

public actual fun newLineChar(): Char = '\n'
