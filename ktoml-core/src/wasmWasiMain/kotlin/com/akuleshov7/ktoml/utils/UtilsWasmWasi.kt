/**
 * Specific implementation for utilities
 */

package com.akuleshov7.ktoml.utils

import org.jetbrains.kotlin.formver.plugin.Borrowed
import org.jetbrains.kotlin.formver.plugin.Unique

@Suppress("LONG_NUMERICAL_VALUES_SEPARATED")
private const val MAX_CODE_POINT = 0x10FFFFf
private const val MIN_SUPPLEMENTARY_CODE_POINT: Int = 0x10000

@Suppress("MAGIC_NUMBER")
internal actual fun @Unique @Borrowed StringBuilder.appendCodePointCompat(codePoint: Int) {
    when (codePoint) {
        in 0 until MIN_SUPPLEMENTARY_CODE_POINT -> append(codePoint.toChar())
        in MIN_SUPPLEMENTARY_CODE_POINT..MAX_CODE_POINT -> {
            append(Char.MIN_HIGH_SURROGATE + ((codePoint - 0x10000) shr 10))
            append(Char.MIN_LOW_SURROGATE + (codePoint and 0x3ff))
        }
        else -> throw IllegalArgumentException()
    }
}

public actual fun newLineChar(): Char = '\n'
