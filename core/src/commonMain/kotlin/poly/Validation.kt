/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core.poly

import polyhedra.model.api.PolyhedronContract
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

/** Validates the abstract source surface and its derived presentation geometry. */
fun Polyhedron.validateRenderableImmersion() {
    require(vs.isNotEmpty()) { "Polyhedron has no vertices" }
    require(fs.isNotEmpty()) { "Polyhedron has no faces" }
    for (v in vs) {
        require(v.x.isFinite() && v.y.isFinite() && v.z.isFinite()) {
            "$v has non-finite coordinates"
        }
        require(v.directedEdges.isNotEmpty()) { "$v is not part of the surface" }
    }
    val lengthTolerance = EPS * circumradius
    for (first in vs.indices) for (second in (first + 1) until vs.size) {
        require(vertexComponentIds[first] != vertexComponentIds[second] ||
            (vs[first] - vs[second]).norm > lengthTolerance) {
            "Distinct source vertices $first and $second have coincident positions"
        }
    }
    // Validate edges
    for (e in es) {
        require((e.a - e.b).norm > lengthTolerance) {
            "$e non-degenerate"
        }
    }
    require(resolvedFaces.size == fs.size)
    for ((faceIndex, resolved) in resolvedFaces.withIndex()) {
        val f = fs[faceIndex]
        require(resolved.sourceFaceId == f.id && resolved.sourceFaceKind == f.kind)
        require(resolved.cells.isNotEmpty()) { "$f has no resolved nonzero-winding cells" }
        require(resolved.vertices.all { vertex ->
            vertex.position.x.isFinite() && vertex.position.y.isFinite() && vertex.position.z.isFinite()
        }) { "$f has a non-finite resolved vertex" }
        for (triangle in resolved.triangles) {
            require(triangle.a in resolved.vertices.indices &&
                triangle.b in resolved.vertices.indices && triangle.c in resolved.vertices.indices
            ) { "$f has an invalid resolved triangle index" }
            val a = resolved.vertices[triangle.a].position
            val b = resolved.vertices[triangle.b].position
            val c = resolved.vertices[triangle.c].position
            val ab = b - a
            val ac = c - a
            val bc = c - b
            val normal = ab cross ac
            // Resolved intersections can create tiny but well-shaped triangles on a large
            // surface. Degeneracy is an aspect-ratio test, not a global minimum face area.
            val areaTolerance = EPS * maxOf(ab * ab, ac * ac, bc * bc)
            require(normal.norm > areaTolerance) { "$f has a degenerate triangle: $a, $b, $c; area2=${normal.norm}, tolerance=$areaTolerance" }
            require(normal * f > EPS * normal.norm) {
                "$f has inconsistent resolved-triangle orientation"
            }
        }
    }

    // Every vertex still has one manifold fan (checked by Polyhedron). Multiple closed
    // components are compounds, not malformed surfaces.
}

/** Legacy rendering/export gate: renderable plus positive aggregate signed volume. */
fun Polyhedron.validateMeshGeometry() {
    validateRenderableImmersion()
    val volumeTolerance = EPS * circumradius * circumradius * circumradius
    require(signedVolume() > volumeTolerance) {
        "Polyhedron surface is inward-facing or has zero signed volume"
    }
}

fun Polyhedron.signedVolume(): Double = resolvedFaces.sumOf { face ->
    face.triangles.sumOf { triangle ->
        val a = face.vertices[triangle.a].position
        val b = face.vertices[triangle.b].position
        val c = face.vertices[triangle.c].position
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
