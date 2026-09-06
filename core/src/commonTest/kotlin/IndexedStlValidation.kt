package polyhedra.core

import polyhedra.core.poly.validateDistinctVertexPositions
import polyhedra.model.api.CoreStlResponse
import polyhedra.model.util.EPS
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times

/** Independently validates an already triangulated export without rebuilding/resolving its faces. */
internal fun CoreStlResponse.validateIndexedStl() {
    require(error == null) { error?.reason.orEmpty() }
    require(vertices.isNotEmpty() && triangles.isNotEmpty())
    require(vertices.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
    val radius = vertices.maxOf { it.norm }
    require(radius.isFinite() && radius > 0.0)
    val components = IndexUnion(vertices.size)
    val fans = IndexUnion(triangles.size * 3)
    val edgeUses = HashMap<Long, Int>()
    fun next(corner: Int): Int = corner / 3 * 3 + (corner % 3 + 1) % 3
    fun vertexAt(corner: Int): Int = triangles[corner / 3].let { triangle ->
        when (corner % 3) { 0 -> triangle.a; 1 -> triangle.b; else -> triangle.c }
    }
    var volume6 = 0.0
    for ((index, triangle) in triangles.withIndex()) {
        val (a, b, c) = triangle
        require(a in vertices.indices && b in vertices.indices && c in vertices.indices)
        val ab = vertices[b] - vertices[a]
        val ac = vertices[c] - vertices[a]
        val bc = vertices[c] - vertices[b]
        val area2 = (ab cross ac).norm
        require(area2.isFinite() && area2 > EPS * maxOf(ab * ab, ac * ac, bc * bc)) {
            "Degenerate triangle $index"
        }
        volume6 += vertices[a] * (vertices[b] cross vertices[c])
        components.union(a, b)
        components.union(b, c)
        for (local in 0..2) {
            val corner = index * 3 + local
            val start = vertexAt(corner)
            val end = vertexAt(next(corner))
            val key = (minOf(start, end).toLong() shl 32) or maxOf(start, end).toLong()
            val previous = edgeUses[key]
            if (previous == null) {
                edgeUses[key] = corner
            } else {
                require(previous >= 0) { "Edge has more than two incident triangles" }
                require(vertexAt(previous) == end && vertexAt(next(previous)) == start) {
                    "Adjacent triangles have inconsistent orientation"
                }
                fans.union(previous, next(corner))
                fans.union(next(previous), corner)
                edgeUses[key] = -1
            }
        }
    }
    require(edgeUses.values.all { it == -1 }) { "Boundary edge in closed STL" }
    edgeUses.clear()
    val vertexFans = IntArray(vertices.size) { -1 }
    for (corner in 0 until triangles.size * 3) {
        val vertex = vertexAt(corner)
        val root = fans.find(corner)
        require(vertexFans[vertex] == -1 || vertexFans[vertex] == root) { "Disconnected vertex fan" }
        vertexFans[vertex] = root
    }
    require(vertexFans.all { it >= 0 }) { "Unreferenced STL vertex" }
    validateDistinctVertexPositions(vertices, IntArray(vertices.size) { components.find(it) }, EPS * radius)
    require(volume6.isFinite() && volume6 / 6.0 > EPS * radius * radius * radius) { "Inward or zero-volume STL" }
}

private class IndexUnion(size: Int) {
    private val parents = IntArray(size) { it }
    fun find(index: Int): Int {
        var root = index
        while (parents[root] != root) {
            parents[root] = parents[parents[root]]
            root = parents[root]
        }
        return root
    }
    fun union(a: Int, b: Int) {
        val first = find(a)
        val second = find(b)
        if (first != second) parents[maxOf(first, second)] = minOf(first, second)
    }
}
