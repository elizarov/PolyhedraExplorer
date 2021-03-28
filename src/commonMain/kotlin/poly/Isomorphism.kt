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

private enum class Dir { L, R }

private class Block(val es: MutableList<Edge>) {
    var lq = false
    var rq = false
    operator fun get(dir: Dir): Boolean = if (dir == Dir.L) lq else rq
    operator fun set(dir: Dir, value: Boolean) { if (dir == Dir.L) lq = value else rq = value }
}

private class Task(val block: Block, val dir: Dir)

// A V*log(V) Algorithm for Isomorphism of Triconnected Planar Graphs
// J. E. Hopcroft and R. E. Tarjan
// Modified to find geometrical isomorphism by starting with geometrical equivalence classes
fun List<Edge>.groupIndistinguishable(): List<List<Edge>> {
    val queue = ArrayList<Task>()
    val blocks: ArrayList<Block> = groupByTo(TreeMap()) { EdgeEquivalenceClass(it) }
        .values
        .mapTo(ArrayList()) { list -> Block(list) }
    val edgeBlock = HashMap<Edge, Block>()
    for (b in blocks) {
        for (e in b.es) edgeBlock[e] = b
    }
    fun enqueue(block: Block, dir: Dir) {
        queue + Task(block, dir)
        block[dir] = true
    }
    for (b in blocks) {
        enqueue(b, Dir.L)
        enqueue(b, Dir.R)
    }
    var qh = 0
    while (qh < queue.size) {
        val task = queue[qh++]
        task.block[task.dir] = false
        val move = task.block.es.mapTo(HashSet()) { e -> if (task.dir == Dir.R) e.rNext else e.lNext }
        val split = move.mapTo(HashSet()) { edgeBlock[it]!! }
        for (b in split) {
            val newEs = b.es.filterTo(ArrayList()) { it in move }
            if (newEs.size == b.es.size) continue
            b.es.removeAll(move)
            val newBlock = Block(newEs)
            blocks += newBlock
            for (e in newEs) edgeBlock[e] = newBlock
            for (dir in Dir.values()) {
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
