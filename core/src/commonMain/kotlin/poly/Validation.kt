/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core.poly

import polyhedra.model.poly.*
import polyhedra.model.util.*

fun Polyhedron.validate() {
    validateGeometry()
    validateKinds()
}

fun Polyhedron.validateGeometry() {
    validateProperGeometry()
    for (f in fs) {
        require(f.isPlanar) {
            "Face is not planar: $f"
        }
    }
}

/** Validates the mesh properties required for safe rendering, while allowing non-planar faces. */
fun Polyhedron.validateMeshGeometry() {
    require(vs.isNotEmpty()) { "Polyhedron has no vertices" }
    require(fs.isNotEmpty()) { "Polyhedron has no faces" }
    for (v in vs) {
        require(v.x.isFinite() && v.y.isFinite() && v.z.isFinite()) {
            "$v has non-finite coordinates"
        }
        require(v.directedEdges.isNotEmpty()) { "$v is not part of the surface" }
    }
    val lengthTolerance = EPS * circumradius
    val areaTolerance = lengthTolerance * circumradius
    // Validate edges
    for (e in es) {
        require((e.a - e.b).norm > lengthTolerance) {
            "$e non-degenerate"
        }
    }
    // Validate faces
    for (f in fs) {
        for (triangle in f.triangles) {
            val a = f[triangle.a]
            val b = f[triangle.b]
            val c = f[triangle.c]
            val normal = (b - a) cross (c - a)
            require(normal.norm > areaTolerance) { "$f has a degenerate triangle" }
            require(normal * f > EPS * normal.norm) {
                "$f has inconsistent boundary orientation"
            }
        }
    }
    val signedVolume = signedVolume()
    val volumeTolerance = EPS * circumradius * circumradius * circumradius
    require(signedVolume > volumeTolerance) {
        "Polyhedron surface is inward-facing or has zero signed volume"
    }

    // A disconnected closed mesh is a compound (or an unused nested shell), not one polyhedron.
    val visited = HashSet<Face>()
    var componentCount = 0
    for (first in fs) {
        if (first in visited) continue
        componentCount++
        val pending = ArrayDeque<Face>()
        pending += first
        while (pending.isNotEmpty()) {
            val face = pending.removeFirst()
            if (!visited.add(face)) continue
            for (edge in face.directedEdges) pending += edge.l
        }
    }
    require(componentCount == 1) {
        "Polyhedron has $componentCount disconnected surface components"
    }
}

fun Polyhedron.signedVolume(): Double = fs.sumOf { face ->
    face.triangles.sumOf { triangle ->
        val a = face[triangle.a]
        val b = face[triangle.b]
        val c = face[triangle.c]
        a * (b cross c) / 6.0
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
