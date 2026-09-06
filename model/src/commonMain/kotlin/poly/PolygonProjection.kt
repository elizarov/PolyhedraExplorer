/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.model.poly

import polyhedra.model.util.*
import kotlin.math.abs

class PolygonProjection(
    val vs: List<Vec3>,
    /** The same basis for derived presentation pieces, not a refit of each piece. */
    val project: (Vec3) -> Vec3 = { it },
) : Comparable<PolygonProjection> {
    override fun compareTo(other: PolygonProjection): Int =
        VertexListApproxComparator.compare(vs, other.vs)

    companion object {
        val Empty = PolygonProjection(emptyList())
    }
}

data class ProjectedFace(
    val face: Face,
    val figure: PolygonProjection,
)

data class EdgeNetProjection(
    val left: ProjectedFace,
    val right: ProjectedFace,
)

infix fun PolygonProjection.approx(other: PolygonProjection) =
    vs.size == other.vs.size &&
    vs.indices.all { i -> vs[i] approx other.vs[i] }

val VertexListApproxComparator : Comparator<List<Vec3>> =
    LexicographicListComparator(Vec3ApproxComparator)

// project face vertices using a given starting index
private fun computeProjectionFigureAt(plane: Plane, vs: List<Vec3>, i: Int): PolygonProjection {
    val v0 = vs[i]
    val c = plane.tangentPoint
    if (v0 approx c) return PolygonProjection.Empty
    val n = vs.size
    val ux = (v0 - c).unit
    fun project(point: Vec3): Vec3 {
        val v = point - c
        return Vec3(ux * v, (ux cross v) * plane, ux * plane)
    }
    val list = ArrayList<Vec3>(n)
    for (j in 0 until n) {
        list += project(vs[(i + j) % n])
    }
    return PolygonProjection(list, ::project)
}

private fun computeProjectionFigure(plane: Plane, vs: List<Vec3>): PolygonProjection =
    vs.indices.maxOfOrNull { i -> computeProjectionFigureAt(plane, vs, i) }!!

fun Face.computeProjectionFigure() =
    computeProjectionFigure(this, fvs)

fun Face.computeProjectionFigureAt(v: Vertex) =
    computeProjectionFigureAt(this, fvs, fvs.indexOf(v))

/**
 * Unfolds the two faces adjacent to this edge into a shared plane. The edge is vertical, centered
 * at the origin, with [Edge.l] on the left and [Edge.r] on the right.
 */
fun Edge.computeNetProjection(): EdgeNetProjection {
    val center = 0.5.atSegment(a, b)
    val vertical = vec.unit
    return EdgeNetProjection(
        ProjectedFace(l, l.computeEdgeProjection(center, vertical, -1.0)),
        ProjectedFace(r, r.computeEdgeProjection(center, vertical, 1.0)),
    )
}

private fun Face.computeEdgeProjection(
    edgeCenter: Vec3,
    vertical: Vec3,
    targetSide: Double,
): PolygonProjection {
    var horizontal = this cross vertical
    if (horizontal.norm < EPS) {
        horizontal = fvs
            .map { vertex ->
                val offset = vertex - edgeCenter
                offset - vertical * (offset * vertical)
            }
            .maxBy { it.norm }
    }
    horizontal = horizontal.unit
    var side = fvs.sumOf { (it - edgeCenter) * horizontal }
    if (abs(side) < EPS) {
        side = fvs
            .map { (it - edgeCenter) * horizontal }
            .maxBy { abs(it) }
    }
    if (side * targetSide < 0.0) horizontal = -horizontal
    fun project(point: Vec3): Vec3 {
        val offset = point - edgeCenter
        return Vec3(offset * horizontal, offset * vertical, 0.0)
    }
    return PolygonProjection(fvs.map(::project), ::project)
}

// use dual to compute vertex figure
fun Vertex.computeProjectFigure() =
    computeProjectionFigure(
        dualPlane(1.0),
        directedEdges.map { it.r.dualPoint(1.0) }
    )

fun Vertex.computeProjectionFigureAt(f: Face) =
    computeProjectionFigureAt(
        dualPlane(1.0),
        directedEdges.map { it.r.dualPoint(1.0) },
        directedEdges.indexOfFirst { it.r == f }
    )
