package polyhedra.common.transform

import polyhedra.common.*
import polyhedra.common.util.*

class ChamferGeometry(val poly: Polyhedron) {
    // Directed edge movement direction over its right face
    val ded = poly.directedEdges.associateWith { e ->
        // face point H on the edge A-B, so that OH is tangent to AB
        val h = e.tangentPoint()
        // vector that bisects the angle between both faces -- normal for the new chamfered face
        val cn = (e.r.plane.n + e.l.plane.n).unit
        // project h onto cn and invert to get the actual edge direction vector
        val de = cn * -(cn * h)
        // normal to the edge in the R face
        val fn = (e.r.plane.n cross (e.a.pt - e.b.pt)).unit
        // project de onto fn to get edge movement vector on the face
        fn * ((de * de) / (fn * de))
    }

    // Face vertex movement direction
    val fvd = poly.faceDirectedEdges.mapValues { (_, fes) ->
        fes.indices.associate { i ->
            val e = fes[i]
            val g = fes[(i + 1) % fes.size]
            check(e.b === g.a)
            val a = ded[e]!!
            val b = ded[g]!!
            val ab = a * b
            val ab2 = ab * ab
            val a2 = a * a
            val b2 = b * b
            val a2b2 = a2 * b2
            val x = (ab * b2 - a2b2) / (ab2 - a2b2)
            val y = (ab * a2 - a2b2) / (ab2 - a2b2)
            e.b to a * x + b * y
        }
    }
}

fun Polyhedron.chamferGeometry() = ChamferGeometry(this)

fun Polyhedron.regularChamferingRatio(edgeKind: EdgeKind? = null): Double =
    chamferGeometry().regularChamferingRatio(edgeKind)

fun ChamferGeometry.regularChamferingRatio(edgeKind: EdgeKind? = null): Double {
    // min for all edge kinds by default
    if (edgeKind == null) return poly.edgeKinds.keys.minOf { regularChamferingRatio(it) }
    // computing for a specific edge
    val e = poly.edgeKinds[edgeKind]!!.first() // representative edge
    val f = e.r // looking at right face
    val ev = e.vec // A -> B vector
    val el = ev.norm
    val eu = ev / el // A -> B unit vector
    val dl = eu * fvd[f]!![e.a]!! - eu * fvd[f]!![e.b]!! // edge length reduction ratio
    // resulting reduced edge length = el - f * dl
    val dv = (e.a.pt + fvd[f]!![e.a]!!).norm
    // result new edge length = f * dv
    return el / (dl + dv)
}

fun Polyhedron.chamfered(): Polyhedron =
    chamferGeometry().chamfered()

fun Polyhedron.chamfered(vr: Double): Polyhedron =
    chamferGeometry().chamfered(vr)

fun ChamferGeometry.chamfered(vr: Double = regularChamferingRatio()): Polyhedron = with(poly) {
    polyhedron {
        // shifted original vertices
        for (v in vs) {
            vertex((1 - vr) * v.pt, v.kind)
        }
        // vertices from the directed edges
        val vertexKindOfs = vertexKinds.size
        val ev = directedEdges.associateWith { e ->
            vertex(e.a.pt + vr * fvd[e.r]!![e.a]!!, VertexKind(vertexKindOfs + directedEdgeKindsIndex[e.kind]!!))
        }
        val fvv = ev.directedEdgeToFaceVertexMap()
        // faces from the original faces
        for (f in fs) {
            val fvs = faceDirectedEdges[f]!!.map { ev[it]!! }
            face(fvs, f.kind)
        }
        // 6-faces from the original edges
        val faceKindOfs = faceKinds.size
        for (e in es) {
            val fvs = listOf(
                fvv[e.r]!![e.a]!!,
                e.a, // face builder call with remap vertex
                fvv[e.l]!![e.a]!!,
                fvv[e.l]!![e.b]!!,
                e.b, // face builder call with remap vertex
                fvv[e.r]!![e.b]!!
            )
            face(fvs, FaceKind(faceKindOfs + edgeKindsIndex[e.kind]!!))
        }
    }
}