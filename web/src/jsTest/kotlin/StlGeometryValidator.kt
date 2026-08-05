package polyhedra.js.poly

import polyhedra.common.util.*
import kotlin.math.abs
import kotlin.math.roundToLong

internal data class StlValidation(
    val triangleCount: Int,
    val degenerateTriangles: Int,
    val boundaryEdges: Int,
    val nonManifoldEdges: Int,
    val signedVolume: Double,
    val solidNamesMatch: Boolean,
) {
    val isValid: Boolean
        get() = triangleCount > 0 &&
            degenerateTriangles == 0 &&
            boundaryEdges == 0 &&
            nonManifoldEdges == 0 &&
            abs(signedVolume) > 1e-9 &&
            solidNamesMatch
}

internal object StlGeometryValidator {
    fun validateAscii(stl: String): StlValidation {
        val lines = stl.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val openingName = lines.firstOrNull()?.takeIf { it.startsWith("solid ") }?.removePrefix("solid ")
        val closingName = lines.lastOrNull()?.takeIf { it.startsWith("endsolid ") }?.removePrefix("endsolid ")
        val vertices = lines.asSequence()
            .filter { it.startsWith("vertex ") }
            .map(::parseVertex)
            .toList()
        require(vertices.size % 3 == 0) { "STL vertex count ${vertices.size} is not divisible by three" }

        var degenerateTriangles = 0
        var signedVolume = 0.0
        val edgeCounts = HashMap<EdgeKey, Int>()
        for (triangle in vertices.chunked(3)) {
            val (a, b, c) = triangle
            val ab = b - a
            val ac = c - a
            val nx = ab.y * ac.z - ab.z * ac.y
            val ny = ab.z * ac.x - ab.x * ac.z
            val nz = ab.x * ac.y - ab.y * ac.x
            val areaSquared = nx * nx + ny * ny + nz * nz
            if (!areaSquared.isFinite() || areaSquared <= 1e-18) {
                degenerateTriangles++
                continue
            }
            signedVolume += (
                a.x * (b.y * c.z - b.z * c.y) -
                    a.y * (b.x * c.z - b.z * c.x) +
                    a.z * (b.x * c.y - b.y * c.x)
                ) / 6.0
            countEdge(edgeCounts, a, b)
            countEdge(edgeCounts, b, c)
            countEdge(edgeCounts, c, a)
        }

        return StlValidation(
            triangleCount = vertices.size / 3,
            degenerateTriangles = degenerateTriangles,
            boundaryEdges = edgeCounts.values.count { it == 1 },
            nonManifoldEdges = edgeCounts.values.count { it > 2 },
            signedVolume = signedVolume,
            solidNamesMatch = openingName != null && openingName == closingName,
        )
    }

    private fun parseVertex(line: String): Vec3 {
        val coordinates = line.removePrefix("vertex ").split(Regex("\\s+"))
        require(coordinates.size == 3) { "Invalid STL vertex: $line" }
        return Vec3(
            coordinates[0].toDouble(),
            coordinates[1].toDouble(),
            coordinates[2].toDouble(),
        )
    }

    private fun countEdge(counts: MutableMap<EdgeKey, Int>, a: Vec3, b: Vec3) {
        val first = PointKey(a)
        val second = PointKey(b)
        val edge = if (first <= second) EdgeKey(first, second) else EdgeKey(second, first)
        counts[edge] = (counts[edge] ?: 0) + 1
    }

    private data class EdgeKey(val a: PointKey, val b: PointKey)

    private data class PointKey(val x: Long, val y: Long, val z: Long) : Comparable<PointKey> {
        constructor(point: Vec3) : this(quantize(point.x), quantize(point.y), quantize(point.z))

        override operator fun compareTo(other: PointKey): Int =
            compareValuesBy(this, other, PointKey::x, PointKey::y, PointKey::z)

        companion object {
            private fun quantize(value: Double): Long = (value * 10_000).roundToLong()
        }
    }
}
