/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.util

class LexicographicListComparator<T>(val comparator: Comparator<T>) : Comparator<List<T>> {
    override fun compare(a: List<T>, b: List<T>): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val c = comparator.compare(a[i], b[i])
            if (c != 0) return c
        }
        if (a.size < b.size) return -1
        if (a.size > b.size) return 1
        return 0
    }
}