/**
 * Specific implementation for utilities
 */

package com.akuleshov7.ktoml.utils

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

internal actual fun @Unique @Borrowed StringBuilder.appendCodePointCompat(codePoint: Int) {
    // Appends char by char, not through appendCodePoint: formver knows `append(Char)` keeps the receiver
    // borrowed, but treats an unspecified Java method as consuming it.
    if (!Character.isValidCodePoint(codePoint)) {
        throw IllegalArgumentException()
    }
    if (Character.isBmpCodePoint(codePoint)) {
        append(codePoint.toChar())
    } else {
        append(Character.highSurrogate(codePoint))
        append(Character.lowSurrogate(codePoint))
    }
}

public actual fun newLineChar(): Char = '\n'
