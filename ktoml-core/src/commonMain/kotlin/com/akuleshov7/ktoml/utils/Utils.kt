/**
 * File with a utilities toolkit
 */

package com.akuleshov7.ktoml.utils

import com.akuleshov7.ktoml.tree.nodes.TableType
import com.akuleshov7.ktoml.tree.nodes.TomlNode
import com.akuleshov7.ktoml.tree.nodes.TomlTable
import org.jetbrains.kotlin.formver.plugin.*

/**
 * @param enumValue input value that we need to compare with elements of enum
 * @return nearest enum value (using levenshtein distance algorithm)
 */
public fun Iterable<String>.closestEnumName(enumValue: String): String? =
    this.minByOrNull { levenshteinDistance(it, enumValue) }

/**
 * Append a code point to a [StringBuilder]
 *
 * @param codePoint code point
 */
@Throws(IllegalArgumentException::class)
internal expect fun @Unique @Borrowed StringBuilder.appendCodePointCompat(codePoint: Int)

/**
 * searching (BFS) the table with the [fullTableName]
 *
 * @param children list of nodes
 * @param fullTableName string with a table name
 */
public fun findPrimitiveTableInAstByName(children: List<TomlNode>, fullTableName: String): TomlTable? {
    if (children.isEmpty()) {
        return null
    }
    children.forEach {
        if (it is TomlTable && it.type == TableType.PRIMITIVE && it.fullTableKey.toString() == fullTableName) {
            return it
        }
    }

    return findPrimitiveTableInAstByName(children.map { it.children }.flatten(), fullTableName)
}

/**
 * Unfortunately Levenshtein method is implemented in jline and not ported to Kotlin Native.
 * So we need to implement it (inspired by: https://pl.kotl.in/ifo0z0vMC)
 *
 * @param first string to compare
 * @param second string for comparison
 * @return the distance between compared strings
 */
@AlwaysVerify
public fun levenshteinDistance(first: String, second: String): Int {
    postconditions<Int> { r -> 0 <= r && r <= first.length + second.length }
    when {
        first == second -> return 0
        first.isEmpty() -> return second.length
        second.isEmpty() -> return first.length
        else -> {
            // this is a generated else block
        }
    }

    val firstLen = first.length + 1
    val secondLen = second.length + 1
    var distance: @Unique IntArray = IntArray(firstLen) { it }
    // Not `IntArray(firstLen) { 0 }`: formver's filling loop for a second array forgets the first one's elements.
    var newDistance: @Unique IntArray = IntArray(firstLen)

    for (i in 1 until secondLen) {
        loopInvariants {
            distance.size == firstLen && newDistance.size == firstLen
            forAll<Int> { m -> (0 <= m && m < firstLen) implies (0 <= distance[m] && distance[m] <= m + i - 1) }
        }
        newDistance[0] = i
        for (j in 1 until firstLen) {
            loopInvariants {
                distance.size == firstLen && newDistance.size == firstLen
                forAll<Int> { m -> (0 <= m && m < firstLen) implies (0 <= distance[m] && distance[m] <= m + i - 1) }
                forAll<Int> { m -> (0 <= m && m < j) implies (0 <= newDistance[m] && newDistance[m] <= m + i) }
            }
            val costReplace = distance[j - 1] + (if (first[j - 1] == second[i - 1]) 0 else 1)
            val costInsert = distance[j] + 1
            val costDelete = newDistance[j - 1] + 1

            newDistance[j] = minOf(costInsert, costDelete, costReplace)
        }
        // A swap through `also` moves an owned local into a lambda capture, which the verifier rejects.
        val swap = distance
        distance = newDistance
        newDistance = swap
    }
    return distance[firstLen - 1]
}
