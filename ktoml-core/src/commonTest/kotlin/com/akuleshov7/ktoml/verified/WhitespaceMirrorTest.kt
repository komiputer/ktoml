package com.akuleshov7.ktoml.verified

import kotlin.test.Test
import kotlin.test.assertEquals

class WhitespaceMirrorTest {
    // The proof only shows that isKotlinWhitespace matches its own postcondition; this checks that the
    // spelled-out character set is the one the shipping code gets from Char.isWhitespace().
    @Test
    fun matchesCharIsWhitespace() {
        val mismatches = (Char.MIN_VALUE..Char.MAX_VALUE)
            .filter { isKotlinWhitespace(it) != it.isWhitespace() }
            .map { "U+" + it.code.toString(16).uppercase().padStart(4, '0') }
        assertEquals(emptyList(), mismatches)
    }
}
