package polyhedra.common.transform

import polyhedra.common.poly.*
import polyhedra.common.util.*
import kotlin.math.*

// a * x^2 + b * x + c = 0
private fun solve3(a: Double, b: Double, c: Double) =
    (-b + sqrt(sqr(b) - 4 * a * c)) / (2 * a)

private fun snubComputeSA(ea: Double, da: Double, cr: Double): Double {
    val rf = 1 - cr
    val cm = (1 - cos(da)) / 2
    val cp = (1 + cos(da)) / 2
    val cosGA = solve3(cp * sqr(rf), 2 * cm * rf * cos(ea), -sqr(cos(ea)) * (cm + sqr(rf)))
    return ea - acos(cosGA)
}

private fun snubComputeA(ea: Double, da: Double, cr: Double, sa: Double): Double {
    val h = 1 / (2 * tan(ea))
    val ga = ea - sa
    val rf = 1 - cr
    val t = rf / (2 * sin(ea))
    return Vec3(
        2 * t * sin(ga),
        (h - t * cos(ga)) * (cos(da) - 1),
        (h - t * cos(ga)) * sin(da)
    ).norm
}

private fun snubComputeB(ea: Double, da: Double, cr: Double, sa: Double): Double {
    val h = 1 / (2 * tan(ea))
    val ga = ea - sa
    val ha = ea + sa
    val rf = 1 - cr
    val t = rf / (2 * sin(ea))
    return Vec3(
        t * (sin(ga) - sin(ha)),
        (h - t * cos(ga)) * cos(da) - (h - t * cos(ha)),
        (h - t * cos(ga)) * sin(da)
    ).norm
}

private fun snubComputeCR(ea: Double, da: Double): Double {
    var crL = 0.0
    var crR = 1.0
    while (true) {
        val cr = (crL + crR) / 2
        if (cr <= crL || cr >= crR) return cr // result precision is an ULP
        val sa = snubComputeSA(ea, da, cr)
        // error goes from positive to negative to NaN as cr goes from 0 to 1
        val rf = 1 - cr
        val err = snubComputeB(ea, da, cr, sa) - rf
        if (err <= 0)
            crL = cr else
            crR = cr
    }
}

data class SnubbingRatio(val cr: Double, val sa: Double) {
    override fun toString(): String = "(cr=${cr.fmt}, sa=${sa.fmt})"
}

fun Polyhedron.regularSnubbingRatio(edgeKind: EdgeKind? = null): SnubbingRatio {
    val (ea, da) = regularFaceGeometry(edgeKind)
    val cr = snubComputeCR(ea, da)
    val sa = snubComputeSA(ea, da, cr)
    return SnubbingRatio(cr, sa)
}

fun Polyhedron.snub(
    sr: SnubbingRatio = regularSnubbingRatio(),
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null
) = transformedPolyhedron(Transform.Snub, sr, scale, forceFaceKinds) {
    val (cr, sa) = sr
    val rr = dualReciprocationRadius
    // vertices from the face-vertices (directed edges)
    val fvv = fs.associateWith { f ->
        val c = f.dualPoint(rr) // for regular polygons -- face center
        val r = f.toRotationAroundQuat(-sa)
        f.directedEdges.associateBy({ it.a }, { e ->
            vertex(c + ((1 - cr) * (e.a - c)).rotated(r), VertexKind(directedEdgeKindsIndex[e.kind]!!))
        })
    }
    // faces from the original faces
    for (f in fs) {
        face(fvv[f]!!.values, f.kind)
    }
    // faces from the original vertices
    var kindOfs = faceKinds.size
    for (v in vs) {
        val fvs = v.directedEdges.map { fvv[it.r]!![v]!! }
        face(fvs, FaceKind(kindOfs + v.kind.id))
    }
    for (vk in vertexKinds.keys) faceKindSource(FaceKind(kindOfs + vk.id), vk)
    // 3-faces from the directed edges
    kindOfs += vertexKinds.size
    for (e in directedEdges) {
        val fvs = listOf(
            fvv[e.l]!![e.a]!!,
            fvv[e.l]!![e.b]!!,
            fvv[e.r]!![e.a]!!
        )
        face(fvs, FaceKind(kindOfs + directedEdgeKindsIndex[e.kind]!!))
    }
    for ((ek, id) in directedEdgeKindsIndex) faceKindSource(FaceKind(kindOfs + id), ek)
    mergeIndistinguishableKinds()
}
