package polyhedra.core.poly

import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.math.abs

private const val INTERSECTION_EPS = 1e-7

/**
 * Verifies that the triangulated surface is an embedded, connected two-manifold. Non-planar faces
 * are supported: their shared deterministic triangulation defines the face surface. Intersections
 * are allowed only along the vertex or edge explicitly shared by the two source faces.
 */
fun Polyhedron.validateProperGeometry() {
    validateMeshGeometry()
    val tolerance = INTERSECTION_EPS * circumradius.coerceAtLeast(1.0)
    val triangles = fs.flatMap { face ->
        face.triangles.map { triangle -> SurfaceTriangle(face, triangle) }
    }.sortedBy(SurfaceTriangle::minX)

    for (firstIndex in triangles.indices) {
        val first = triangles[firstIndex]
        for (secondIndex in (firstIndex + 1) until triangles.size) {
            val second = triangles[secondIndex]
            if (second.minX > first.maxX + tolerance) break
            if (first.face == second.face || !first.boundsOverlap(second, tolerance)) continue
            val intersections = triangleIntersection(first, second, tolerance)
            if (intersections.isEmpty()) continue
            val sharedVertices = first.vertexIds.intersect(second.vertexIds).map { id -> vs[id] }
            require(intersections.all { point -> point.onSharedFeature(sharedVertices, tolerance * 10.0) }) {
                "Faces ${first.face.id} and ${second.face.id} intersect outside their shared " +
                    "boundary (shared vertices: ${sharedVertices.map { vertex -> "${vertex.id}:${vertex.toPreciseString()}" }}; " +
                    "intersection: ${intersections.joinToString(transform = Vec3::toPreciseString)})"
            }
        }
    }
}

private class SurfaceTriangle(face: Face, triangle: FaceTriangle) {
    val face: Face = face
    val a: Vec3 = face[triangle.a]
    val b: Vec3 = face[triangle.b]
    val c: Vec3 = face[triangle.c]
    val vertexIds: Set<Int> = setOf(face[triangle.a].id, face[triangle.b].id, face[triangle.c].id)
    val minX = minOf(a.x, b.x, c.x)
    val maxX = maxOf(a.x, b.x, c.x)
    private val minY = minOf(a.y, b.y, c.y)
    private val maxY = maxOf(a.y, b.y, c.y)
    private val minZ = minOf(a.z, b.z, c.z)
    private val maxZ = maxOf(a.z, b.z, c.z)

    fun boundsOverlap(other: SurfaceTriangle, tolerance: Double): Boolean =
        minY <= other.maxY + tolerance && other.minY <= maxY + tolerance &&
            minZ <= other.maxZ + tolerance && other.minZ <= maxZ + tolerance
}

private fun triangleIntersection(
    first: SurfaceTriangle,
    second: SurfaceTriangle,
    tolerance: Double,
): List<Vec3> {
    val firstNormal = ((first.b - first.a) cross (first.c - first.a)).unit
    val secondNormal = ((second.b - second.a) cross (second.c - second.a)).unit
    val coplanar = (firstNormal cross secondNormal).norm <= INTERSECTION_EPS &&
        abs((second.a - first.a) * firstNormal) <= tolerance
    val points = ArrayList<Vec3>()
    if (coplanar) {
        addCoplanarIntersections(points, first, second, firstNormal, tolerance)
    } else {
        addPlaneIntersections(points, first, second, secondNormal, tolerance)
        addPlaneIntersections(points, second, first, firstNormal, tolerance)
    }
    return points.distinctApprox(tolerance)
}

private fun addPlaneIntersections(
    result: MutableList<Vec3>,
    source: SurfaceTriangle,
    target: SurfaceTriangle,
    targetNormal: Vec3,
    tolerance: Double,
) {
    val sourceVertices = listOf(source.a, source.b, source.c)
    for (index in sourceVertices.indices) {
        val a = sourceVertices[index]
        val b = sourceVertices[(index + 1) % sourceVertices.size]
        val da = (a - target.a) * targetNormal
        val db = (b - target.a) * targetNormal
        if (abs(da) <= tolerance && pointInTriangle(a, target, tolerance)) result += a
        if (da * db < 0.0) {
            val point = (da / (da - db)).atSegment(a, b)
            if (pointInTriangle(point, target, tolerance)) result += point
        }
    }
}

private fun pointInTriangle(point: Vec3, triangle: SurfaceTriangle, tolerance: Double): Boolean {
    val v0 = triangle.b - triangle.a
    val v1 = triangle.c - triangle.a
    val v2 = point - triangle.a
    val d00 = v0 * v0
    val d01 = v0 * v1
    val d11 = v1 * v1
    val d20 = v2 * v0
    val d21 = v2 * v1
    val denominator = d00 * d11 - d01 * d01
    if (abs(denominator) <= EPS) return false
    val v = (d11 * d20 - d01 * d21) / denominator
    val w = (d00 * d21 - d01 * d20) / denominator
    val barycentricTolerance = tolerance / maxOf(v0.norm, v1.norm, (triangle.c - triangle.b).norm)
    return v >= -barycentricTolerance && w >= -barycentricTolerance &&
        v + w <= 1.0 + barycentricTolerance
}

private fun addCoplanarIntersections(
    result: MutableList<Vec3>,
    first: SurfaceTriangle,
    second: SurfaceTriangle,
    normal: Vec3,
    tolerance: Double,
) {
    val firstVertices = listOf(first.a, first.b, first.c)
    val secondVertices = listOf(second.a, second.b, second.c)
    firstVertices.filterTo(result) { pointInTriangle(it, second, tolerance) }
    secondVertices.filterTo(result) { pointInTriangle(it, first, tolerance) }
    for (firstIndex in firstVertices.indices) {
        val firstNext = (firstIndex + 1) % firstVertices.size
        for (secondIndex in secondVertices.indices) {
            val secondNext = (secondIndex + 1) % secondVertices.size
            addCoplanarSegmentIntersections(
                result,
                firstVertices[firstIndex],
                firstVertices[firstNext],
                secondVertices[secondIndex],
                secondVertices[secondNext],
                normal,
                tolerance,
            )
        }
    }
}

private data class Vec2(val x: Double, val y: Double) {
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
}

private fun Vec3.projectFor(normal: Vec3): Vec2 {
    val ax = abs(normal.x)
    val ay = abs(normal.y)
    val az = abs(normal.z)
    return when {
        ax >= ay && ax >= az -> Vec2(y, z)
        ay >= az -> Vec2(x, z)
        else -> Vec2(x, y)
    }
}

private infix fun Vec2.cross(other: Vec2): Double = x * other.y - y * other.x

private fun addCoplanarSegmentIntersections(
    result: MutableList<Vec3>,
    a3: Vec3,
    b3: Vec3,
    c3: Vec3,
    d3: Vec3,
    normal: Vec3,
    tolerance: Double,
) {
    val a = a3.projectFor(normal)
    val b = b3.projectFor(normal)
    val c = c3.projectFor(normal)
    val d = d3.projectFor(normal)
    val r = b - a
    val s = d - c
    val denominator = r cross s
    val projectedTolerance = tolerance * maxOf(r.norm, s.norm, 1.0)
    if (abs(denominator) > projectedTolerance) {
        val ca = c - a
        val t = (ca cross s) / denominator
        val u = (ca cross r) / denominator
        val parameterTolerance = tolerance / maxOf(r.norm, s.norm, 1.0)
        if (t in -parameterTolerance..(1.0 + parameterTolerance) &&
            u in -parameterTolerance..(1.0 + parameterTolerance)
        ) result += t.coerceIn(0.0, 1.0).atSegment(a3, b3)
        return
    }
    if (abs((c - a) cross r) > projectedTolerance) return
    if (a.onSegment(c, d, tolerance)) result += a3
    if (b.onSegment(c, d, tolerance)) result += b3
    if (c.onSegment(a, b, tolerance)) result += c3
    if (d.onSegment(a, b, tolerance)) result += d3
}

private val Vec2.norm: Double get() = kotlin.math.sqrt(x * x + y * y)

private fun Vec2.onSegment(a: Vec2, b: Vec2, tolerance: Double): Boolean =
    x >= minOf(a.x, b.x) - tolerance && x <= maxOf(a.x, b.x) + tolerance &&
        y >= minOf(a.y, b.y) - tolerance && y <= maxOf(a.y, b.y) + tolerance

private fun List<Vec3>.distinctApprox(tolerance: Double): List<Vec3> {
    val result = ArrayList<Vec3>(size)
    for (point in this) {
        if (result.none { existing -> (point - existing).norm <= tolerance }) result += point
    }
    return result
}

private fun Vec3.onSharedFeature(sharedVertices: List<Vertex>, tolerance: Double): Boolean = when (sharedVertices.size) {
    1 -> (this - sharedVertices.single()).norm <= tolerance
    2 -> distanceToSegment(sharedVertices[0], sharedVertices[1]) <= tolerance
    else -> false
}

private fun Vec3.distanceToSegment(a: Vec3, b: Vec3): Double {
    val edge = b - a
    val lengthSquared = edge * edge
    if (lengthSquared <= EPS) return (this - a).norm
    val fraction = (((this - a) * edge) / lengthSquared).coerceIn(0.0, 1.0)
    return (this - fraction.atSegment(a, b)).norm
}
