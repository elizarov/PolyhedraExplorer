/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.poly

import polyhedra.common.util.*

class EdgeEquivalenceClass(e: Edge) : Comparable<EdgeEquivalenceClass> {
    val pa = e.a.computeProjectionFigureAt(e.r)
    val pb = e.b.computeProjectionFigureAt(e.l)
    val pl = e.l.computeProjectionFigureAt(e.b)
    val pr = e.r.computeProjectionFigureAt(e.a)

    override fun compareTo(other: EdgeEquivalenceClass): Int {
        val ca = pa.compareTo(other.pa)
        if (ca != 0) return ca
        val cb = pb.compareTo(other.pb)
        if (cb != 0) return cb
        val cl = pl.compareTo(other.pl)
        if (cl != 0) return cl
        return pr.compareTo(other.pr)
    }
}

class IsoEdge(
    val kind: EdgeKind,
    val eq: EdgeEquivalenceClass
) {
    lateinit var lNext: IsoEdge
    lateinit var rNext: IsoEdge

    operator fun get(dir: IsoDir): IsoEdge = when(dir) {
        IsoDir.L -> lNext
        IsoDir.R -> rNext
    }

    override fun toString(): String = kind.toString()
}

val Polyhedron.isoEdges: List<IsoEdge>
    get() {
        val edgesByKind = directedEdges.associateBy({ it.kind }, { it })
        val isoEdgesByKind = edgesByKind.entries.associateBy(
            keySelector = { (kind, _) -> kind },
            valueTransform = { (kind, edge) ->
                IsoEdge(kind, EdgeEquivalenceClass(edge))
            },
        )
        for (isoEdge in isoEdgesByKind.values) {
            isoEdge.lNext = isoEdgesByKind[edgesByKind[isoEdge.kind]!!.next(IsoDir.L).kind]!!
            isoEdge.rNext = isoEdgesByKind[edgesByKind[isoEdge.kind]!!.next(IsoDir.R).kind]!!
        }
        return isoEdgesByKind.values.toList()
    }

private class Block(val es: MutableList<IsoEdge>) {
    var lq = false
    var rq = false
    operator fun get(dir: IsoDir): Boolean = when (dir) {
        IsoDir.L -> lq
        IsoDir.R -> rq
    }
    operator fun set(dir: IsoDir, value: Boolean) = when (dir) {
        IsoDir.L -> lq = value
        IsoDir.R -> rq = value
    }
}

private class Task(val block: Block, val dir: IsoDir)

// A V*log(V) Algorithm for Isomorphism of Triconnected Planar Graphs
// J. E. Hopcroft and R. E. Tarjan
// Modified to find geometrical isomorphism by starting with geometrical equivalence classes
fun List<IsoEdge>.groupIndistinguishable(): List<List<IsoEdge>> {
    val queue = ArrayList<Task>()
    val blocks: ArrayList<Block> = groupByTo(TreeMap()) { it.eq }
        .values
        .mapTo(ArrayList()) { list -> Block(list) }
    val edgeBlock = HashMap<IsoEdge, Block>()
    for (b in blocks) {
        for (e in b.es) edgeBlock[e] = b
    }
    fun enqueue(block: Block, dir: IsoDir) {
        queue + Task(block, dir)
        block[dir] = true
    }
    for (b in blocks) {
        enqueue(b, IsoDir.L)
        enqueue(b, IsoDir.R)
    }
    var qh = 0
    while (qh < queue.size) {
        val task = queue[qh++]
        task.block[task.dir] = false
        val move = task.block.es.mapTo(HashSet()) { e -> e[task.dir] }
        val split = move.mapTo(HashSet()) { edgeBlock[it]!! }
        for (b in split) {
            val newEs = b.es.filterTo(ArrayList()) { it in move }
            if (newEs.size == b.es.size) continue
            b.es.removeAll(move)
            val newBlock = Block(newEs)
            blocks += newBlock
            for (e in newEs) edgeBlock[e] = newBlock
            for (dir in IsoDir.values()) {
                when {
                    b[dir] -> enqueue(newBlock, dir)
                    newEs.size <= b.es.size -> enqueue(newBlock, dir)
                    else -> enqueue(b, dir)
                }
            }
        }
    }
    return blocks.map { it.es }
}

/**
 * Builds the normalized local-geometry signature used for catalog seed recognition.
 *
 * The edge projection figures are independent of global rotation and vertex numbering but preserve handedness.
 * Comparing their sorted multiset recognizes a specific chiral realization reached through a different
 * transform path, while still distinguishing its reflected counterpart and solids with only matching F/E/V.
 */
internal class PolyhedronGeometryFingerprint(
    private val edgeClasses: List<EdgeClassCount>,
) {
    fun matches(other: PolyhedronGeometryFingerprint): Boolean =
        edgeClasses.size == other.edgeClasses.size &&
            edgeClasses.indices.all { index -> edgeClasses[index].compareTo(other.edgeClasses[index]) == 0 }
}

internal class EdgeClassCount(
    val edgeClass: EdgeEquivalenceClass,
    val count: Int,
) : Comparable<EdgeClassCount> {
    override fun compareTo(other: EdgeClassCount): Int {
        val classComparison = edgeClass.compareTo(other.edgeClass)
        return if (classComparison != 0) classComparison else count.compareTo(other.count)
    }
}

internal fun Polyhedron.geometryFingerprint(): PolyhedronGeometryFingerprint =
    scaled(Scale.Circumradius).let { normalized ->
        PolyhedronGeometryFingerprint(normalized.geometryFingerprintOrientation())
    }

private fun Polyhedron.geometryFingerprintOrientation(): List<EdgeClassCount> {
    val counts = directedEdges.groupingBy { it.kind }.eachCount()
    return isoEdges
        .map { edge -> EdgeClassCount(edge.eq, counts.getValue(edge.kind)) }
        .sorted()
}

/** Reflects the geometry and reverses every face so its normal remains outward-facing. */
internal fun Polyhedron.reflected(): Polyhedron {
    val reflectedVertices = vs.map { vertex ->
        MutableVertex(vertex.id, Vec3(-vertex.x, vertex.y, vertex.z), vertex.kind)
    }
    val reflectedVerticesById = reflectedVertices.associateBy { vertex -> vertex.id }
    val reflectedFaces = fs.map { face ->
        MutableFace(
            face.id,
            face.fvs.asReversed().map { vertex -> reflectedVerticesById.getValue(vertex.id) },
            face.kind,
        )
    }
    return Polyhedron(reflectedVertices, reflectedFaces, faceKindSources)
}
