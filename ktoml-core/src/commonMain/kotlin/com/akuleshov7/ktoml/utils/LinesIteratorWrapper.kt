package com.akuleshov7.ktoml.utils

import org.jetbrains.kotlin.formver.plugin.NeverVerify

/**
 * @param T
 * @param iterator
 */
internal class LinesIteratorWrapper<T>(
    private val iterator: Iterator<T>
) : Iterator<T> {
    internal var lineNo: Int = 0
        private set

    override fun hasNext(): Boolean = iterator.hasNext()

    // Not verified: the line counter has no upper bound, so `++` may overflow.
    @NeverVerify
    override fun next(): T {
        lineNo++
        return iterator.next()
    }
}
