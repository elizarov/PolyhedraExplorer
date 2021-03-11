/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common

import polyhedra.common.util.*

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
    val es = faceDirectedEdges[f]!!
    val size = es.size
    val vfs = List(size) { VertexFaceKind(es[it].a.kind, es[it].l.kind) }
    return FaceKindEssence(f.kind, f.d, vfs.minCycle())
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