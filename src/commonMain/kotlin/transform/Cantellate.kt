package polyhedra.common.transform

import polyhedra.common.poly.*
import polyhedra.common.util.*
import kotlin.math.*

data class RegularFaceGeometry(
    val ea: Double, // PI / face_size
    val da: Double  // dihedral angle
)

fun Polyhedron.regularFaceGeometry(edgeKind: EdgeKind? = null): RegularFaceGeometry {
    val ek = edgeKind ?: edgeKinds.keys.first() // min edge kind by default
    val e = edgeKinds[ek]!! // representative edge
    val f = e.r // primary face
    val g = e.l // secondary face
    val n = f.size // primary face size
    val ea = PI / n
    val da = PI - acos(f * g) // dihedral angle
    return RegularFaceGeometry(ea, da)
}

fun Polyhedron.regularCantellationRatio(edgeKind: EdgeKind? = null): Double {
    val (ea, da) = regularFaceGeometry(edgeKind)
    return 1 / (1 + sin(da / 2) / tan(ea))
}

fun Polyhedron.cantellated(
    cr: Double = regularCantellationRatio(),
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null
): Polyhedron = transformedPolyhedron(Transform.Cantellated, cr, scale, forceFaceKinds) {
    val rr = dualReciprocationRadius
    // vertices from the directed edges
    val ev = directedEdges.associateWith { e ->
        val a = e.a // vertex for cantellation
        val f = e.r // primary face for cantellation
        val c = f.dualPoint(rr) // for regular polygons -- face center
        vertex(cr.atSegment(a, c), VertexKind(directedEdgeKindsIndex[e.kind]!!))
    }
    val fvv = ev.directedEdgeToFaceVertexMap()
    // faces from the original faces
    for (f in fs) {
        val fvs = f.directedEdges.map { ev[it]!! }
        face(fvs, f.kind)
    }
    // faces from the original vertices
    var kindOfs = faceKinds.size
    for (v in vs) {
        face(v.directedEdges.map { ev[it]!! }, FaceKind(kindOfs + v.kind.id))
    }
    for (vk in vertexKinds.keys) faceKindSource(FaceKind(kindOfs + vk.id), vk)
    // 4-faces from the original edges
    kindOfs += vertexKinds.size
    for (e in es) {
        val fvs = listOf(
            fvv[e.r]!![e.a]!!,
            fvv[e.l]!![e.a]!!,
            fvv[e.l]!![e.b]!!,
            fvv[e.r]!![e.b]!!
        )
        face(fvs, FaceKind(kindOfs + edgeKindsIndex[e.kind]!!))
    }
    for ((ek, id) in edgeKindsIndex) faceKindSource(FaceKind(kindOfs + id), ek)
    mergeIndistinguishableKinds()
}

fun Polyhedron.dual(): Polyhedron = transformedPolyhedron(Transform.Dual) {
    val rr = dualReciprocationRadius
    // vertices from the original faces
    val fv = fs.associateWith { f ->
        vertex(f.dualPoint(rr), VertexKind(f.kind.id))
    }
    // faces from the original vertices
    for (v in vs) {
        face(v.directedEdges.map { fv[it.r]!! }, FaceKind(v.kind.id))
    }
    for (vk in vertexKinds.keys) faceKindSource(FaceKind(vk.id), vk)
}