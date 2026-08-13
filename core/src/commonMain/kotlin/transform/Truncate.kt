package polyhedra.core.transform

import polyhedra.core.poly.*
import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.math.*

fun Polyhedron.rectified(
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null,
): Polyhedron = transformedPolyhedron(
    Transform.Rectified,
    scale = scale,
    forceFaceKinds = forceFaceKinds,
) {
    // vertices from the original edges
    val ev = es.associateWith { e ->
        vertex(e.midPoint(edgesMidPointDefault), VertexKind(edgeKindsIndex[e.kind]!!))
    }
    // faces from the original faces
    for (f in fs) {
        face(f.directedEdges.map { ev[it.normalizedDirection()]!! }, f.kind)
    }
    // faces from the original vertices
    val kindOfs = faceKinds.size
    for (v in vs) {
        face(v.directedEdges.map { ev[it.normalizedDirection()]!! }, FaceKind(kindOfs + v.kind.id))
    }
    for (vk in vertexKinds.keys) faceKindSource(FaceKind(kindOfs + vk.id), vk)
    mergeIndistinguishableKinds()
}

// ea == PI / face_size
fun regularTruncationRatio(ea: Double): Double = 1 / (1 + cos(ea))

fun Polyhedron.regularTruncationRatio(faceKind: FaceKind = FaceKind(0)): Double {
    val f = faceKinds[faceKind]!! // take representative face of this kind
    val halfStepAngle = if (resolvedFaces[f.id].sourceBoundarySelfIntersects) {
        val center = f.fvs.fold<Vertex, Vec3>(Vec3.ZERO) { sum, vertex -> sum + vertex } / f.size.toDouble()
        f.fvs.indices.map { index ->
            val a = (f.fvs[index] - center).unit
            val b = (f.fvs[(index + 1) % f.size] - center).unit
            acos((a * b).coerceIn(-1.0, 1.0)) / 2.0
        }.average()
    } else {
        PI / f.size
    }
    return regularTruncationRatio(halfStepAngle)
}

fun Polyhedron.truncated(
    tr: Double = regularTruncationRatio(),
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null
): Polyhedron {
    require(tr.isFinite() && tr >= 0.0) { "Truncation depth must be finite and non-negative" }
    if (tr approx 1.0) return rectified(scale, forceFaceKinds)
    return transformedPolyhedron(Transform.Truncated, tr, scale, forceFaceKinds) {
    // vertices from the original directed edges
    val ev = directedEdges.associateWith { e ->
        val t = tr * e.midPointFraction(edgesMidPointDefault)
        vertex(t.atSegment(e.a, e.b), VertexKind(directedEdgeKindsIndex[e.kind]!!))
    }
    // faces from the original faces
    for (f in fs) {
        val fvs = f.directedEdges.flatMap {
            listOf(ev[it]!!, ev[it.reversed]!!)
        }
        face(fvs, f.kind)
    }
    // faces from the original vertices
    val kindOfs = faceKinds.size
    for (v in vs) {
        face(v.directedEdges.map { ev[it]!! }, FaceKind(kindOfs + v.kind.id))
    }
    for (vk in vertexKinds.keys) faceKindSource(FaceKind(kindOfs + vk.id), vk)
    mergeIndistinguishableKinds()
}
}
