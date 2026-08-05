package polyhedra.common.transform

import polyhedra.common.poly.*
import polyhedra.common.util.*
import kotlin.math.*

data class BevellingRatio(val cr: Double, val tr: Double) {
    override fun toString(): String = "(cr=${cr.fmt}, tr=${tr.fmt})"
}

fun Polyhedron.regularBevellingRatio(edgeKind: EdgeKind? = null): BevellingRatio {
    val (ea, da) = regularFaceGeometry(edgeKind)
    val tr = regularTruncationRatio(ea)
    val cr = (1 - tr) / (1 + sin(da / 2) / tan(ea) - tr)
    return BevellingRatio(cr, tr)
}

fun Polyhedron.bevelled(
    br: BevellingRatio = regularBevellingRatio(),
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null
): Polyhedron = transformedPolyhedron(Transform.Bevelled, br, scale, forceFaceKinds) {
    val (cr, tr) = br
    val rr = dualReciprocationRadius
    // vertices from the face-directed edges
    val fev = fs.associateWith { f ->
        val c = f.dualPoint(rr) // for regular polygons -- face center
        f.directedEdges.flatMap { e ->
            val kind = directedEdgeKindsIndex[e.kind]!!
            val a = e.a
            val b = e.b
            val ac = cr.atSegment(a, c)
            val bc = cr.atSegment(b, c)
            val mf = e.midPointFraction(edgesMidPointDefault)
            val t1 = tr * mf
            val t2 = tr * (1 - mf)
            listOf(
                e to vertex(t1.atSegment(ac, bc), VertexKind(2 * kind)),
                e.reversed to vertex(t2.atSegment(bc, ac), VertexKind(2 * kind + 1))
            )
        }.associate { it }
    }
    // faces from the original faces
    for (f in fs) {
        face(fev[f]!!.values, f.kind)
    }
    // faces from the original vertices
    var kindOfs = faceKinds.size
    for (v in vs) {
        val fvs = v.directedEdges.flatMap { e ->
            listOf(fev[e.l]!![e]!!, fev[e.r]!![e]!!)
        }
        face(fvs, FaceKind(kindOfs + v.kind.id))
    }
    for (vk in vertexKinds.keys) faceKindSource(FaceKind(kindOfs + vk.id), vk)
    // 4-faces from the original edges
    kindOfs += vertexKinds.size
    for (e in es) {
        val er = e.reversed
        val fvs = listOf(
            fev[e.r]!![e]!!,
            fev[e.l]!![e]!!,
            fev[e.l]!![er]!!,
            fev[e.r]!![er]!!
        )
        face(fvs, FaceKind(kindOfs + edgeKindsIndex[e.kind]!!))
    }
    for ((ek, id) in edgeKindsIndex) faceKindSource(FaceKind(kindOfs + id), ek)
    mergeIndistinguishableKinds()
}