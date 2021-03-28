/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.poly

import polyhedra.common.util.*
import kotlin.math.*

data class VertexFaceKind(
    val vk: VertexKind,
    val fk: FaceKind
) : Comparable<VertexFaceKind> {
    override fun compareTo(other: VertexFaceKind): Int {
        if (vk != other.vk) return vk.compareTo(other.vk)
        return fk.compareTo(other.fk)
    }

    override fun toString(): String = "$vk $fk"
}

class FaceKindEssence(
    val kind: FaceKind,
    val dist: Double,
    val isPlanar: Boolean,
    val vfs: List<VertexFaceKind>,
    val figure: PolygonProjection
) {
    fun approx(other: FaceKindEssence): Boolean =
        kind == other.kind &&
        dist approx other.dist &&
        vfs == other.vfs &&
        figure approx other.figure
    
    override fun toString() =
        "distance ${dist.fmt}, " +
        "adj ${vfs.size} [${vfs.joinToString(" ")}]"
}

fun Face.essence(): FaceKindEssence {
    val fes = directedEdges
    val size = fes.size
    val vfs = List(size) { VertexFaceKind(fes[it].a.kind, fes[it].l.kind) }
    val figure = computeFigure(this, fvs)
    return FaceKindEssence(kind, d, isPlanar, vfs.minCycle(), figure)
}

private fun computeFigure(plane: Plane, vs: List<Vec3>): PolygonProjection {
    // project face vertices using all possible starting vertices
    val c = plane.tangentPoint
    val n = vs.size
    val pvs = vs.withIndex().mapNotNull { (i0, v0) ->
        if (v0 approx c) return@mapNotNull null
        val ux = (v0 - c).unit
        val list = ArrayList<Vec3>(n)
        for (j in 0 until n) {
            val v = vs[(i0 + j) % n] - c
            val x = ux * v
            val y = (ux cross v) * plane
            val z = ux * plane
            list += Vec3(x, y, z)
        }
        list
    }
    // select the maximum among them
    return PolygonProjection(pvs.maxWithOrNull(VertexListApproxComparator)!!)
}

class VertexKindEssence(
    val kind: VertexKind,
    val dist: Double,
    val vfs: List<VertexFaceKind>,
    val figure: PolygonProjection
) {
    fun approx(other: VertexKindEssence): Boolean =
        kind == other.kind &&
        dist approx other.dist &&
        vfs == other.vfs &&
        figure approx other.figure

    override fun toString() =
        "distance ${dist.fmt}, " +
        "adj ${vfs.size} [${vfs.joinToString(" ")}]"
}

fun Vertex.essence(): VertexKindEssence {
    val ves = directedEdges
    val size = ves.size
    val vfs = List(size) { VertexFaceKind(ves[it].b.kind, ves[it].r.kind) }
    // use dual to compute figure 
    val figure = computeFigure(dualPlane(1.0), directedEdges.map { it.r.dualPoint(1.0) })
    return VertexKindEssence(kind, norm, vfs.minCycle(), figure)
}

class EdgeKindEssence(
    val kind: EdgeKind,
    val dist: Double,
    val len: Double,
    val dihedralAngle: Double
) {
    fun approx(other: EdgeKindEssence): Boolean =
        kind == other.kind &&
        dist approx other.dist &&
        len approx other.len &&
        dihedralAngle approx other.dihedralAngle
}

fun Edge.essence(): EdgeKindEssence {
    val dist = midPoint(MidPoint.Closest).norm
    val dihedralAngle = PI - acos(l * r)
    return EdgeKindEssence(kind, dist, len, dihedralAngle)
}

fun <T : Comparable<T>> List<T>.minCycle(): List<T> {
    var min = 0
    for (i in 1 until size) {
        var cmp = 0
        for (j in 0 until size) {
            val c = get((i + j) % size).compareTo(get((min + j) % size))
            if (c != 0) {
                cmp = c
                break
            }
        }
        if (cmp < 0) min = i
    }
    return List(size) { get((min + it) % size) }
}