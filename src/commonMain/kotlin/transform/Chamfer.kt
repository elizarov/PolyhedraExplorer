/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.transform

import polyhedra.common.poly.*
import polyhedra.common.util.*

enum class ChamferAngle {
    // chamfer normally to the plane that goes through edge and origin
    Orthonormal,
    // chamfer normally to the bisector between planes that meet at the edge
    Bisector,
    // chamfer in such a way that both faces are evenly cut towards their respective tangent points
    FaceRegular
}

enum class ChamferLimit {
    // chamfer till the new edges have the same length
    EdgeRegular,
    // chamfer till the new face distance is an average between original face distances
    // (resulting is circumscribed over sphere, dual is inscribed into sphere)
    FaceDistanceAverage
}

val defaultChamferAngle = ChamferAngle.Bisector
val defaultChamferLimit = ChamferLimit.EdgeRegular

class ChamferGeometry(val poly: Polyhedron, angle: ChamferAngle) {
    // Edge movement direction vector
    val edgeDir = poly.es.associateWith { e ->
        // face point H on the edge A-B, so that OH is tangent to AB
        val h = e.tangentPoint()
        // compute normal for the new chamfered face
        val cn = when (angle) {
            // face normal to the edge plane
            ChamferAngle.Orthonormal -> h
            // face along the vector that bisects the angle between both faces --
            ChamferAngle.Bisector -> e.r + e.l
            // angle that cuts both faces evenly
            ChamferAngle.FaceRegular -> {
                // both face planes
                val f = e.r
                val g = e.l
                // meet at tangent points on faces
                val fc = f.tangentPoint
                val gc = g.tangentPoint
                // distance from the point to the edge
                val fd = e.distanceTo(fc)
                val gd = e.distanceTo(gc)
                // resulting vector v = x * fn + y * gn
                // where x * gd == y * fd, so with x = 1 we get
                val y = gd / fd
                f + y * g
            }
        }.unit // unit vector
        // project h onto cn and invert to get the actual edge direction vector
        cn * -(cn * h)
    }

    // Directed edge movement direction over its right face
    val directedEdgeFaceDir = poly.directedEdges.associateWith { e ->
        val de = edgeDir[e.normalizedDirection()]!!
        // normal to the edge in the R face
        val fn = (e.r cross (e.a - e.b)).unit
        // project de onto fn to get edge movement vector on the face
        fn * ((de * de) / (fn * de))
    }

    // Face vertex movement direction
    val fvd = poly.fs.associateWith { f ->
        val fes = f.directedEdges
        fes.indices.associate { i ->
            val e = fes[i]
            val g = fes[(i + 1) % fes.size]
            check(e.b === g.a)
            val a = directedEdgeFaceDir[e]!!
            val b = directedEdgeFaceDir[g]!!
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

fun Polyhedron.chamferGeometry(angle: ChamferAngle = defaultChamferAngle) =
    ChamferGeometry(this, angle)

fun Polyhedron.chamferingRatio(edgeKind: EdgeKind? = null): Double =
    chamferGeometry().chamferingRatio(edgeKind)

fun ChamferGeometry.chamferingRatio(edgeKind: EdgeKind? = null, limit: ChamferLimit = defaultChamferLimit): Double {
    // min for all edge kinds by default
    if (edgeKind == null) return poly.edgeKinds.keys.minOf { chamferingRatio(it) }
    // computing for a specific edge
    val e = poly.edgeKinds[edgeKind]!! // representative edge
    return when (limit) {
        ChamferLimit.EdgeRegular -> {
            val f = e.r // looking at right face
            val ev = e.vec // A -> B vector
            val el = ev.norm
            val eu = ev / el // A -> B unit vector
            val dl = eu * fvd[f]!![e.a]!! - eu * fvd[f]!![e.b]!! // edge length reduction ratio
            // resulting reduced edge length = el - f * dl
            val dv = (e.a + fvd[f]!![e.a]!!).norm
            // result new edge length = f * dv
            el / (dl + dv)
        }
        ChamferLimit.FaceDistanceAverage -> {
            // target distance -- average
            val target = (e.r.d + e.l.d) / 2
            // direction of edge chamfering
            val dir = edgeDir[e.normalizedDirection()]!!
            // cur distance (a zero chamfering fraction)
            val cur = -(dir.unit * e.a)
            (cur - target) / dir.norm
        }
    }
}

fun Polyhedron.chamfered(): Polyhedron = transformedPolyhedron(Transform.Chamfered) {
    chamferGeometry().chamferedTo(this)
}

fun Polyhedron.chamfered(
    vr: Double,
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null
): Polyhedron = transformedPolyhedron(Transform.Chamfered, vr, scale, forceFaceKinds) {
    chamferGeometry().chamferedTo(this, vr)
}

private fun ChamferGeometry.chamferedTo(builder: PolyhedronBuilder, vr: Double = chamferingRatio()) = with(poly) {
    with(builder) {
        // shifted original vertices
        for (v in vs) {
            vertex((1 - vr) * v, v.kind)
        }
        // vertices from the directed edges
        val vertexKindOfs = vertexKinds.size
        val ev = directedEdges.associateWith { e ->
            vertex(e.a + vr * fvd[e.r]!![e.a]!!, VertexKind(vertexKindOfs + directedEdgeKindsIndex[e.kind]!!))
        }
        val fvv = ev.directedEdgeToFaceVertexMap()
        // faces from the original faces
        for (f in fs) {
            val fvs = f.directedEdges.map { ev[it]!! }
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
        for ((ek, id) in edgeKindsIndex) faceKindSource(FaceKind(faceKindOfs + id), ek)
        mergeIndistinguishableKinds()
    }
}