/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

import polyhedra.common.*
import polyhedra.common.transform.*

fun main() {
    val seeds = Seeds.filter { it.type == SeedType.Platonic }
    val transforms = listOf(Transform.Truncated, Transform.Dual, Transform.Rectified, Transform.Cantellated)
    fun found(d: Int, seed: Seed, ts: List<Transform>, poly: Polyhedron): Boolean {
        if (d == 0) return try {
            poly.validate()
            false
        } catch(e: Exception) {
            println("Found invalid transform sequence")
            println("Seed = $seed")
            println("Transforms: $ts")
            true
        }
        for (t in transforms) {
            if (found(d - 1, seed, ts + t, poly.transformed(t))) {
                return true
            }
        }
        return false
    }
    var depth = 0
    loop@while (true) {
        depth++
        for (s in seeds) {
            if (found(depth, s, listOf(), s.poly)) {
                break@loop
            }
        }
    }
}