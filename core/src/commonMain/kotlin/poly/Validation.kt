/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.poly

import polyhedra.common.util.*

fun Polyhedron.validate() {
    validateGeometry()
    validateKinds()
}

fun Polyhedron.validateGeometry() {
    // Validate edges
    for (e in es) {
        require((e.a - e.b).norm > EPS) {
            "$e non-degenerate"
        }
    }
    // Validate faces
    for (f in fs) {
        require(f.d > 0) {
            "Face normal does not point outwards: $f $f "
        }
        for (v in f.fvs)
            require(f.isPlanar) {
                "Face is not planar: $f"
            }
        for (i in 0 until f.size) {
            val a = f[i]
            val b = f[(i + 1) % f.size]
            val c = f[(i + 2) % f.size]
            val rot = (c - a) cross (b - a)
            require(rot * f > -EPS) {
                "Face is not clockwise: $f, vertices $a $b $c"
            }
        }
    }
}

fun Polyhedron.validateKinds() {
    // Validate face kinds
    for ((fk, fs) in fs.groupBy { it.kind }) {
        fs.validateUnique("$fk faces", FaceKindEssence::approx) { it.essence() }
    }
    check(contiguousFaceKinds()) { "Face kinds must be contiguously numbered" }
    // Validate vertex kinds
    for ((vk, vs) in vs.groupBy { it.kind }) {
        vs.validateUnique("$vk vertices", VertexKindEssence::approx) { it.essence() }
    }
    check(contiguousVertexKinds()) { "Vertex kinds must be contiguously numbered" }
    // Validate edge kinds
    for ((ek, es) in es.groupBy { it.kind }) {
        es.validateUnique("$ek edges", EdgeKindEssence::approx) { it.essence() }
    }
}

private fun <T, K> List<T>.validateUnique(msg: String, approx: (K, K) -> Boolean, selector: (T) -> K) {
    val first = first()
    val firstKey = selector(first)
    for (i in 1 until size) {
        val cur = get(i)
        val curKey = selector(cur)
        require(approx(firstKey, curKey)) {
            "$msg are different:\n" +
                "  $first -- $firstKey\n" +
                "  $cur -- $curKey"
        }
    }
}