package polyhedra.core.poly

import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.math.roundToLong

/** Disjoint union, deliberately not a coordinate weld or a physical Boolean union. */
fun compound(members: List<Polyhedron>): Polyhedron {
    require(members.isNotEmpty()) { "A compound needs at least one member" }
    if (members.size == 1) return members.single()
    return polyhedron(mergeIndistinguishableKinds = true) {
        var offset = 0
        var faceKindOffset = 0
        var vertexKindOffset = 0
        for (member in members) {
            member.vs.forEach { vertex(it, VertexKind(vertexKindOffset + it.kind.id)) }
            member.fs.forEach { face ->
                face(face.fvs.map { it.id + offset }, FaceKind(faceKindOffset + face.kind.id))
            }
            offset += member.vs.size
            faceKindOffset += member.faceKinds.keys.maxOf { it.id } + 1
            vertexKindOffset += member.vertexKinds.keys.maxOf { it.id } + 1
        }
    }
}

/** Keeps global coordinates and kind identity while giving each component local vertex IDs. */
fun Polyhedron.componentPolyhedra(): List<Polyhedron> = if (!isCompound) listOf(this) else components.map { faces ->
    val vertices = faces.flatMap { it.fvs }.distinctBy { it.id }.sortedBy { it.id }
    val ids = vertices.withIndex().associate { it.value.id to it.index }
    polyhedron {
        vertices.forEach { vertex(it) }
        faces.forEach { face -> face(face.fvs.map { ids.getValue(it.id) }, face.kind) }
    }
}

/** A rotation/scale/index-independent tie-breaker which still distinguishes mirror arrangements. */
internal fun Polyhedron.rotationInvariantFaceKey(): List<Long> {
    val inverseRadius = 1.0 / circumradius
    val order = Comparator<List<Long>>(::compareGeometryKeys)
    return directedEdges.mapNotNull { edge ->
        val x = edge.a.unit
        val tangent = edge.b - x * (edge.b * x)
        if (tangent.norm < circumradius * 1e-8) return@mapNotNull null
        val y = tangent.unit
        val z = x cross y
        val points = vs.map { point ->
            listOf(x, y, z).map { axis -> (point * axis * inverseRadius * 1e7).roundToLong() }
        }
        fs.map { face ->
            val cycle = face.fvs.map { points[it.id] }
            cycle.indices.map { start ->
                cycle.indices.flatMap { cycle[(start + it) % cycle.size] }
            }.minWith(order).let { listOf(face.fvs.size.toLong()) + it }
        }.sortedWith(order).flatten()
    }.minWith(order)
}

internal fun compareGeometryKeys(first: List<Long>, second: List<Long>): Int {
    for (index in 0 until minOf(first.size, second.size)) {
        val comparison = first[index].compareTo(second[index])
        if (comparison != 0) return comparison
    }
    return first.size.compareTo(second.size)
}

/**
 * Builds a surface from geometric face circuits, splitting distinct manifold vertex fans. This
 * preserves touching compound members without mistaking their shared positions for shared topology.
 */
internal fun surfaceFromCycles(
    points: List<Vec3>,
    cycles: List<List<Int>>,
    kinds: List<FaceKind> = cycles.indices.map(::FaceKind),
): Polyhedron {
    val offsets = IntArray(cycles.size + 1)
    cycles.indices.forEach { offsets[it + 1] = offsets[it] + cycles[it].size }
    val parents = IntArray(offsets.last()) { it }
    fun root(index: Int): Int {
        var current = index
        while (parents[current] != current) {
            parents[current] = parents[parents[current]]
            current = parents[current]
        }
        return current
    }
    fun join(a: Int, b: Int) { parents[root(a)] = root(b) }
    data class Use(val a: Int, val b: Int, val first: Int, val second: Int)
    val uses = linkedMapOf<Pair<Int, Int>, MutableList<Use>>()
    cycles.forEachIndexed { face, cycle ->
        cycle.indices.forEach { i ->
            val j = (i + 1) % cycle.size
            val a = cycle[i]
            val b = cycle[j]
            uses.getOrPut(minOf(a, b) to maxOf(a, b), ::arrayListOf) +=
                Use(a, b, offsets[face] + i, offsets[face] + j)
        }
    }
    for ((edge, pair) in uses) {
        require(pair.size == 2) { "Surface edge $edge has ${pair.size} faces; expected two" }
        val (a, b) = pair
        require(a.a == b.b && a.b == b.a) { "Surface edge $edge has inconsistent orientation" }
        join(a.first, b.second)
        join(a.second, b.first)
    }
    return polyhedron(mergeIndistinguishableKinds = true) {
        val vertexIds = linkedMapOf<Int, Int>()
        cycles.forEachIndexed { face, cycle ->
            val ids = cycle.mapIndexed { index, point ->
                vertexIds.getOrPut(root(offsets[face] + index)) {
                    vertex(points[point], VertexKind(vertexIds.size)).id
                }
            }
            face(ids, kinds[face])
        }
    }
}
