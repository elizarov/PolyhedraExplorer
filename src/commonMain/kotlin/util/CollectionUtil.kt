/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.util

inline fun <T> Iterable<T>.avgOf(selector: (T) -> Double): Double {
    var n = 0
    var sum = 0.0
    for (e in this) {
        n++
        sum += selector(e)
    }
    return sum / n
}

fun <T> List<T>.updatedAt(index: Int, value: T): List<T> {
    val result = toMutableList()
    result[index] = value
    return result
}

fun <T> List<T>.removedAt(index: Int): List<T> {
    val result = toMutableList()
    result.removeAt(index)
    return result
}

inline fun <T, R> Sequence<T>.distinctIndexed(transform: (Int) -> R): Map<T, R> {
    val result = mutableMapOf<T, R>()
    var index = 0
    for (e in this) {
        if (e !in result) result[e] = transform(index++)
    }
    return result
}

