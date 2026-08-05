import polyhedra.common.util.*
import kotlin.random.*
import kotlin.test.*

/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

class TreeMapTest {
    @Test
    fun testLinearAdds() {
        val n = 100
        val t = TreeMap<Int, String>()
        for (i in 0 until n) {
            t[i] = i.toString()
            check(t.keys.toList() == (0..i).toList())
            check(t.values.toList() == (0..i).map { it.toString() })
        }
        for (i in 0 until n) {
            check(i in t)
        }
        for (i in 0 until n) {
            check(t[i] == i.toString())
        }
    }

    @Test
    fun testRandomAdds() {
        val n = 100
        val t = TreeMap<Int, String>()
        val r = (0 until n).shuffled(Random(1))
        for (i in 0 until n) {
            t[r[i]] = r[i].toString()
            val keys = r.subList(0, i + 1).sorted()
            check(t.keys.toList() == keys)
            check(t.values.toList() == keys.map { it.toString() })
        }
        for (i in 0 until n) {
            check(i in t)
        }
        for (i in 0 until n) {
            check(t[r[i]] == r[i].toString())
        }
    }
}