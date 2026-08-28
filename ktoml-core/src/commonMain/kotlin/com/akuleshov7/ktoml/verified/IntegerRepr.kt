package com.akuleshov7.ktoml.verified

import org.jetbrains.kotlin.formver.plugin.*

/**
 * Phase 1 chunk 4 (plan.md §1.2): `writers/IntegerRepresentation.kt`.
 *
 * `IntegerRepresentation` itself is a Kotlin `enum class`; SnaKt's supported-feature list
 * (plan.md §1, "user classes with fields, interfaces and overrides") does not mention enum
 * classes, and no other mirror in this fork uses one. Per this chunk's brief ("if the enum shape
 * resists mirroring, log how and mirror the dispatch logic only"): rather than spend a build cycle
 * discovering how the plugin's `PureLinearizer` handles (or rejects) an `enum class` with
 * properties, the five cases are represented here as an `Int` tag (`0`=BINARY, `1`=DECIMAL,
 * `2`=GROUPED, `3`=HEX, `4`=OCTAL) and the two functions below mirror exactly the
 * `radix`/`prefix` dispatch logic the real enum's constructor arguments encode -- same values,
 * same `when`-over-five-cases shape, without touching the enum class construct itself. Logged as
 * friction (docs/verification/friction.md): whether SnaKt supports enum classes at all is
 * untested by this fork.
 */

/**
 * Mirrors `IntegerRepresentation.radix` (tag order: BINARY=0, DECIMAL=1, GROUPED=2, HEX=3,
 * OCTAL=4, matching the enum's declared order in `IntegerRepresentation.kt`).
 *
 * Proved: `radix >= 2` for every case, the tag-to-radix mapping case by case, and that radix 10 is
 * returned only for `DECIMAL`/`GROUPED` -- which, with the `preconditions` block, makes the `when`'s
 * `else` branch unreachable. That last clause is what gives the precondition provable work to do:
 * without it every postcondition is satisfied by the `else` branch as well.
 */
@AlwaysVerify
@Pure
internal fun integerRepresentationRadix(tag: Int): Int {
    preconditions {
        0 <= tag && tag <= 4
    }
    postconditions<Int> { r ->
        (tag == 0) implies (r == 2)
        (tag == 1) implies (r == 10)
        (tag == 2) implies (r == 10)
        (tag == 3) implies (r == 16)
        (tag == 4) implies (r == 8)
        (r == 10) implies (tag == 1 || tag == 2)
        r >= 2
    }
    return when (tag) {
        0 -> 2
        1 -> 10
        2 -> 10
        3 -> 16
        4 -> 8
        else -> 10
    }
}

/**
 * Mirrors `IntegerRepresentation.prefix` (same tag order as [integerRepresentationRadix]).
 *
 * Proved: `prefix.length` is always `0` or `2` (`"0b"`/`"0x"`/`"0o"` are two characters;
 * `DECIMAL`/`GROUPED` default to `""`), the tag-to-prefix mapping case by case, and that the empty
 * prefix belongs only to `DECIMAL`/`GROUPED` -- which makes the `when`'s `else` branch unreachable
 * and the precondition load-bearing, same as [integerRepresentationRadix].
 */
@AlwaysVerify
internal fun integerRepresentationPrefix(tag: Int): String {
    preconditions {
        0 <= tag && tag <= 4
    }
    postconditions<String> { r ->
        (tag == 0) implies (r == "0b")
        (tag == 1) implies (r == "")
        (tag == 2) implies (r == "")
        (tag == 3) implies (r == "0x")
        (tag == 4) implies (r == "0o")
        (r.length == 0) implies (tag == 1 || tag == 2)
        r.length == 0 || r.length == 2
    }
    return when (tag) {
        0 -> "0b"
        1 -> ""
        2 -> ""
        3 -> "0x"
        4 -> "0o"
        else -> ""
    }
}
