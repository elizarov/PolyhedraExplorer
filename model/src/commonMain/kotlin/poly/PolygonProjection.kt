/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.poly

import polyhedra.common.util.*

class PolygonProjection(
    val vs: List<Vec3>
) : Comparable<PolygonProjection> {
    override fun compareTo(other: PolygonProjection): Int =
        VertexListApproxComparator.compare(vs, other.vs)

    companion object {
        val Empty = PolygonProjection(emptyList())
    }
}

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
    val list = ArrayList<Vec3>(n)
    for (j in 0 until n) {
        val v = vs[(i + j) % n] - c
        val x = ux * v
        val y = (ux cross v) * plane
        val z = ux * plane
        list += Vec3(x, y, z)
    }
    return PolygonProjection(list)
}

private fun computeProjectionFigure(plane: Plane, vs: List<Vec3>): PolygonProjection =
    vs.indices.maxOfOrNull { i -> computeProjectionFigureAt(plane, vs, i) }!!

fun Face.computeProjectionFigure() =
    computeProjectionFigure(this, fvs)

fun Face.computeProjectionFigureAt(v: Vertex) =
    computeProjectionFigureAt(this, fvs, fvs.indexOf(v))

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
