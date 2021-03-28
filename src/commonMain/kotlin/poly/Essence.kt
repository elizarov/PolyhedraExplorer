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
) {
    fun approx(other: FaceKindEssence): Boolean =
        kind == other.kind &&
        dist approx other.dist &&
        vfs == other.vfs
    
    override fun toString() =
        "distance ${dist.fmt}, " +
        "adj ${vfs.size} [${vfs.joinToString(" ")}]"
}

fun Polyhedron.faceEssence(f: Face): FaceKindEssence {
    val fes = f.directedEdges
    val size = fes.size
    val vfs = List(size) { VertexFaceKind(fes[it].a.kind, fes[it].l.kind) }
    return FaceKindEssence(f.kind, f.d, f.isPlanar, vfs.minCycle())
}

class VertexKindEssence(
    val kind: VertexKind,
    val dist: Double,
    val vfs: List<VertexFaceKind>
) {
    fun approx(other: VertexKindEssence): Boolean =
        kind == other.kind &&
        dist approx other.dist &&
        vfs == other.vfs

    override fun toString() =
        "distance ${dist.fmt}, " +
        "adj ${vfs.size} [${vfs.joinToString(" ")}]"
}

fun Polyhedron.vertexEssence(v: Vertex): VertexKindEssence {
    val es = vertexDirectedEdges[v]!!
    val size = es.size
    val vfs = List(size) { VertexFaceKind(es[it].b.kind, es[it].r.kind) }
    return VertexKindEssence(v.kind, v.norm, vfs.minCycle())
}

class EdgeKindEssense(
    val kind: EdgeKind,
    val dist: Double,
    val len: Double,
    val dihedralAngle: Double
)

fun Polyhedron.edgeEssence(e: Edge): EdgeKindEssense {
    val dist = e.midPoint(MidPoint.Closest).norm
    val dihedralAngle = PI - acos(e.l * e.r)
    return EdgeKindEssense(e.kind, dist, e.len, dihedralAngle)
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