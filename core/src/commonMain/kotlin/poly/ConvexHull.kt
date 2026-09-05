package polyhedra.core.poly

import polyhedra.core.util.OperationProgressContext
import polyhedra.model.poly.Polyhedron
import polyhedra.model.util.*
import kotlin.math.abs

// Distances are measured after translating and scaling the input to a unit bounding box.
private const val HULL_TOLERANCE = 1e-9

/**
 * Quickhull with exclusive outside sets. Only points orphaned by deleted visible facets need
 * repartitioning. Coplanar triangles are merged before constructing the polyhedral surface;
 * interior points, coincident compound vertices and collinear edge points do not survive.
 * The returned vertices retain their original coordinates (no recentering or rescaling).
 */
fun convexHull(input: List<Vec3>, progress: OperationProgressContext? = null): Polyhedron {
    require(input.size >= 4 && input.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }) {
        "Convex hull needs at least four finite points"
    }
    val low = Vec3(input.minOf { it.x }, input.minOf { it.y }, input.minOf { it.z })
    val high = Vec3(input.maxOf { it.x }, input.maxOf { it.y }, input.maxOf { it.z })
    val scale = (high - low).norm
    require(scale > 0.0) { "Convex hull points coincide" }
    val center = low + (high - low) * 0.5
    val points = input.map { (it - center) / scale }
    val a = points.indices.minBy { points[it].x }
    val b = points.indices.maxBy { (points[it] - points[a]).norm }
    val axis = (points[b] - points[a]).unit
    val c = points.indices.maxBy { ((points[it] - points[a]) cross axis).norm }
    require(((points[c] - points[a]) cross axis).norm > HULL_TOLERANCE) {
        "Convex hull points are collinear"
    }
    val normal = ((points[b] - points[a]) cross (points[c] - points[a])).unit
    val d = points.indices.maxBy { abs((points[it] - points[a]) * normal) }
    require(abs((points[d] - points[a]) * normal) > HULL_TOLERANCE) {
        "Convex hull points are coplanar"
    }
    val interior = (points[a] + points[b] + points[c] + points[d]) / 4.0
    fun triangle(a: Int, b: Int, c: Int): HullTriangle {
        val n = ((points[b] - points[a]) cross (points[c] - points[a])).unit
        return if ((interior - points[a]) * n < 0.0) {
            HullTriangle(a, b, c, n, points[a])
        } else HullTriangle(a, c, b, n * -1.0, points[a])
    }
    val facets = arrayListOf(triangle(a, b, c), triangle(a, d, b), triangle(b, d, c), triangle(c, d, a))
    fun assign(index: Int, candidates: List<HullTriangle>): Boolean {
        val face = candidates.maxByOrNull { it.distance(points[index]) } ?: return false
        if (face.distance(points[index]) <= HULL_TOLERANCE) return false
        face.outside += index
        return true
    }
    var remaining = points.indices.count { assign(it, facets) }
    progress?.reportProgress(5)
    while (remaining > 0) {
        val first = facets.first { it.outside.isNotEmpty() }
        val eye = first.outside.maxBy { first.distance(points[it]) }
        val visible = facets.filter { it.distance(points[eye]) > HULL_TOLERANCE }.toSet()
        val horizon = linkedSetOf<Pair<Int, Int>>()
        for (face in visible) for (edge in face.edges) {
            if (!horizon.remove(edge.second to edge.first)) horizon += edge
        }
        val orphans = visible.flatMap { it.outside }
        remaining -= orphans.size
        facets.removeAll(visible)
        val added = horizon.map { (start, end) -> triangle(start, end, eye) }
        require(added.size >= 3) { "Convex hull has a degenerate horizon" }
        for (index in orphans) if (index != eye && assign(index, added)) remaining++
        facets += added
        progress?.reportProgress(5 + 80 * (points.size - remaining) / points.size)
    }

    // Adjacent coplanar facets form one supporting polygon. A 2D hull removes obsolete seed
    // simplex points and intermediate straight-edge vertices as well as triangulation diagonals.
    val edgeFaces = HashMap<Pair<Int, Int>, HullTriangle>()
    for (face in facets) for (edge in face.edges) edgeFaces[edge] = face
    val visited = HashSet<HullTriangle>()
    val cycles = arrayListOf<List<Int>>()
    for (first in facets) {
        if (!visited.add(first)) continue
        val group = arrayListOf(first)
        var next = 0
        while (next < group.size) {
            for ((start, end) in group[next++].edges) {
                val neighbor = edgeFaces.getValue(end to start)
                if (neighbor !in visited && (neighbor.normal - first.normal).norm < HULL_TOLERANCE &&
                    neighbor.ids.all { abs(first.distance(points[it])) < HULL_TOLERANCE }) {
                    visited += neighbor
                    group += neighbor
                }
            }
        }
        val ids = group.flatMap { it.ids }.distinct()
        cycles += supportingPolygon(ids, points, first.normal)
    }
    progress?.reportProgress(90)
    val used = cycles.flatten().distinct().sorted()
    val newIds = used.withIndex().associate { it.value to it.index }
    val hull = polyhedron {
        used.forEach { vertex(input[it]) }
        // Quickhull uses outward CCW circuits; the model stores outward clockwise faces.
        cycles.forEach { cycle -> face(cycle.asReversed().map(newIds::getValue)) }
    }.withGeometricKinds()
    progress?.reportProgress(100)
    return hull
}

private class HullTriangle(val a: Int, val b: Int, val c: Int, val normal: Vec3, val anchor: Vec3) {
    val ids = listOf(a, b, c)
    val edges = listOf(a to b, b to c, c to a)
    val outside = arrayListOf<Int>()
    fun distance(point: Vec3): Double = (point - anchor) * normal
}

/** Monotone chain in the supporting plane, returned counterclockwise about its outward normal. */
private fun supportingPolygon(ids: List<Int>, points: List<Vec3>, normal: Vec3): List<Int> {
    val origin = points[ids.first()]
    val u = (points[ids.maxBy { (points[it] - origin).norm }] - origin).unit
    val v = normal cross u
    val ordered = ids.sortedWith(compareBy<Int> { (points[it] - origin) * u }.thenBy { (points[it] - origin) * v })
    fun half(order: List<Int>): List<Int> {
        val result = arrayListOf<Int>()
        for (id in order) {
            while (result.size >= 2) {
                val a = points[result[result.lastIndex - 1]]
                val b = points[result.last()]
                val cross = ((b - a) cross (points[id] - b)) * normal
                if (cross > HULL_TOLERANCE * (b - a).norm) break
                result.removeAt(result.lastIndex)
            }
            result += id
        }
        return result.dropLast(1)
    }
    return half(ordered) + half(ordered.asReversed())
}
