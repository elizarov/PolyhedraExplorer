package polyhedra.core.poly

import polyhedra.core.transform.directDual
import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.math.abs
import kotlin.math.atan2

/** Convex hull of a small seed point set; coplanar supporting triangles become one face. */
internal fun convexSeedHull(points: List<Vec3>): Polyhedron {
    val tolerance = points.maxOf { it.norm } * 1e-7
    val faces = linkedMapOf<List<Int>, List<Int>>()
    for (a in points.indices) for (b in a + 1 until points.size) for (c in b + 1 until points.size) {
        val cross = (points[b] - points[a]) cross (points[c] - points[a])
        if (cross.norm <= tolerance * tolerance) continue
        var normal = cross.unit
        var distance = normal * points[a]
        if (distance < 0.0) { normal *= -1.0; distance = -distance }
        if (distance <= tolerance || points.any { normal * it > distance + tolerance }) continue
        val ids = points.indices.filter { abs(normal * points[it] - distance) <= tolerance }
        if (ids in faces) continue
        val center = normal * distance
        val u = (points[ids.first()] - center).unit
        val v = normal cross u
        val cycle = ids.sortedBy { atan2((points[it] - center) * v, (points[it] - center) * u) }
        faces[ids] = if (cycle.map(points::get).averagePlane() * normal >= 0.0) cycle else cycle.asReversed()
    }
    return polyhedron {
        points.forEach { vertex(it) }
        faces.values.forEach { face(it) }
    }
}

/** Finds every inscribed regular tetrahedron by equal edge lengths, without index recipes. */
private fun regularTetrahedra(points: List<Vec3>): List<List<Int>> {
    val tolerance = points.maxOf { it.norm } * 1e-7
    val result = arrayListOf<List<Int>>()
    for (a in points.indices) for (b in a + 1 until points.size) {
        val edge = (points[a] - points[b]).norm
        for (c in b + 1 until points.size) {
            if (abs((points[a] - points[c]).norm - edge) > tolerance ||
                abs((points[b] - points[c]).norm - edge) > tolerance) continue
            for (d in c + 1 until points.size) {
                if (listOf(a, b, c).all { abs((points[it] - points[d]).norm - edge) <= tolerance }) {
                    result += listOf(a, b, c, d)
                }
            }
        }
    }
    return result
}

internal val twoTetrahedraGeometry: Polyhedron by lazy {
    val points = Seed.Cube.poly.vs
    compound(regularTetrahedra(points).map { convexSeedHull(it.map(points::get)) })
}

private val dodecahedralTetrahedra by lazy { regularTetrahedra(dodecahedronGeometry.vs) }

internal val fiveTetrahedraGeometry: Polyhedron by lazy {
    val points = dodecahedronGeometry.vs
    val first = dodecahedralTetrahedra.first()
    val orbit = dodecahedronGeometry.geometricSymmetryOperations().proper.map { operation ->
        first.map { operation.vertexPermutation[it] }.sorted()
    }.distinct()
    check(orbit.size == 5)
    compound(orbit.map { convexSeedHull(it.map(points::get)) })
}

internal val tenTetrahedraGeometry: Polyhedron by lazy {
    val points = dodecahedronGeometry.vs
    check(dodecahedralTetrahedra.size == 10)
    compound(dodecahedralTetrahedra.map { convexSeedHull(it.map(points::get)) })
}

internal val fiveCubesGeometry: Polyhedron by lazy {
    val points = dodecahedronGeometry.vs
    val cubes = dodecahedralTetrahedra.map { tetrahedron ->
        (tetrahedron + tetrahedron.map { id -> points.indices.minBy { (points[it] + points[id]).norm } }).sorted()
    }.distinct()
    check(cubes.size == 5)
    compound(cubes.map { convexSeedHull(it.map(points::get)) })
}

internal val fiveOctahedraGeometry: Polyhedron by lazy { fiveCubesGeometry.directDual() }
