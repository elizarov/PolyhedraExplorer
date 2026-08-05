package polyhedra.common.transform

import polyhedra.common.poly.*
import polyhedra.common.util.*
import kotlin.math.*

fun Polyhedron.rectified(): Polyhedron = transformedPolyhedron(Transform.Rectified) {
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
    return regularTruncationRatio(PI / f.size)
}

fun Polyhedron.truncated(
    tr: Double = regularTruncationRatio(),
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null
): Polyhedron = transformedPolyhedron(Transform.Truncated, tr, scale, forceFaceKinds) {
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