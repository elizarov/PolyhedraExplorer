package polyhedra.model.poly

import polyhedra.model.util.*
import kotlin.math.abs

/**
 * Indices of one outward-facing triangle of a [Face].
 *
 * Face boundaries are stored clockwise when viewed from outside. Triangle indices are returned in
 * the opposite (counter-clockwise) order expected by WebGL and solid-export formats.
 */
data class FaceTriangle(val a: Int, val b: Int, val c: Int)

/** Whether the face boundary is convex in its average plane. */
val Face.isConvex: Boolean
    get() = indicesAround(size).all { (previous, current, next) ->
        val turn = (this[next] - this[previous]) cross (this[current] - this[previous])
        turn * this >= -EPS * turn.norm
    }

private fun indicesAround(size: Int): List<Triple<Int, Int, Int>> = List(size) { current ->
    Triple((current + size - 1) % size, current, (current + 1) % size)
}

/**
 * Triangulates a simple planar cycle and returns counter-clockwise triangles when viewed along
 * [normal]. The cycle orientation is explicit so derived polygonal regions do not need a [Face].
 */
fun triangulatePlanarPolygon(
    vertices: List<Vec3>,
    normal: Vec3,
    counterClockwise: Boolean,
): List<FaceTriangle> {
    if (!counterClockwise) return triangulateFace(vertices, normal)
    val lastIndex = vertices.lastIndex
    return triangulateFace(vertices.asReversed(), normal).map { triangle ->
        FaceTriangle(
            lastIndex - triangle.a,
            lastIndex - triangle.b,
            lastIndex - triangle.c,
        )
    }
}

internal fun triangulateFace(vertices: List<Vec3>, normal: Vec3): List<FaceTriangle> {
    require(vertices.size >= 3) { "A face needs at least three vertices" }
    require(normal.norm > EPS) { "Face has no well-defined normal" }

    val projected = vertices.map { vertex -> vertex.projectAlongDominantAxis(normal) }
    val scale = projected.maxCoordinateSpan()
    val linearTolerance = EPS * scale
    val areaTolerance = linearTolerance * scale
    val area = projected.signedDoubleArea()
    require(abs(area) > areaTolerance) { "Face has zero projected area" }
    projected.requireSimple(linearTolerance, areaTolerance)

    val orientation = if (area > 0.0) 1.0 else -1.0
    val remaining = vertices.indices.toMutableList()
    val result = ArrayList<FaceTriangle>(vertices.size - 2)
    while (remaining.size > 3) {
        var clipped = false
        for (position in remaining.indices) {
            val previous = remaining[(position + remaining.size - 1) % remaining.size]
            val current = remaining[position]
            val next = remaining[(position + 1) % remaining.size]
            val turn = orientation * projected.cross(previous, current, next)
            if (turn <= areaTolerance) continue
            if (remaining.any { candidate ->
                    candidate != previous && candidate != current && candidate != next &&
                        projected[candidate].insideOrOnTriangle(
                            projected[previous],
                            projected[current],
                            projected[next],
                            orientation,
                            areaTolerance,
                        )
                }
            ) continue
            if (remaining.size == 4) {
                val final = remaining.filterIndexed { candidatePosition, _ -> candidatePosition != position }
                if (abs(projected.cross(final[0], final[1], final[2])) <= areaTolerance) continue
            }

            // Reverse the clockwise face boundary to produce an outward-facing triangle.
            result += FaceTriangle(previous, next, current)
            remaining.removeAt(position)
            clipped = true
            break
        }
        require(clipped) { "Face cannot be triangulated without overlap or degeneracy" }
    }
    result += FaceTriangle(remaining[0], remaining[2], remaining[1])
    return result
}

private data class Vec2(val x: Double, val y: Double)

private fun Vec3.projectAlongDominantAxis(normal: Vec3): Vec2 {
    val ax = abs(normal.x)
    val ay = abs(normal.y)
    val az = abs(normal.z)
    return when {
        ax >= ay && ax >= az -> Vec2(y, z)
        ay >= az -> Vec2(x, z)
        else -> Vec2(x, y)
    }
}

private fun List<Vec2>.maxCoordinateSpan(): Double {
    val minX = minOf(Vec2::x)
    val maxX = maxOf(Vec2::x)
    val minY = minOf(Vec2::y)
    val maxY = maxOf(Vec2::y)
    return maxOf(maxX - minX, maxY - minY)
}

private fun List<Vec2>.signedDoubleArea(): Double = indices.sumOf { index ->
    val next = this[(index + 1) % size]
    this[index].x * next.y - next.x * this[index].y
}

private fun List<Vec2>.cross(a: Int, b: Int, c: Int): Double =
    (this[b].x - this[a].x) * (this[c].y - this[a].y) -
        (this[b].y - this[a].y) * (this[c].x - this[a].x)

private fun Vec2.insideOrOnTriangle(
    a: Vec2,
    b: Vec2,
    c: Vec2,
    orientation: Double,
    tolerance: Double,
): Boolean =
    orientation * cross(a, b, this) >= -tolerance &&
        orientation * cross(b, c, this) >= -tolerance &&
        orientation * cross(c, a, this) >= -tolerance

private fun cross(a: Vec2, b: Vec2, c: Vec2): Double =
    (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

private fun List<Vec2>.requireSimple(linearTolerance: Double, areaTolerance: Double) {
    for (first in indices) {
        val firstNext = (first + 1) % size
        for (second in (first + 1) until size) {
            val secondNext = (second + 1) % size
            if (first == second || firstNext == second || secondNext == first) continue
            require(!segmentsIntersect(
                this[first],
                this[firstNext],
                this[second],
                this[secondNext],
                linearTolerance,
                areaTolerance,
            )) {
                "Face boundary intersects itself"
            }
        }
    }
}

private fun segmentsIntersect(
    a: Vec2,
    b: Vec2,
    c: Vec2,
    d: Vec2,
    linearTolerance: Double,
    areaTolerance: Double,
): Boolean {
    val abc = cross(a, b, c)
    val abd = cross(a, b, d)
    val cda = cross(c, d, a)
    val cdb = cross(c, d, b)
    if (((abc > areaTolerance && abd < -areaTolerance) ||
            (abc < -areaTolerance && abd > areaTolerance)) &&
        ((cda > areaTolerance && cdb < -areaTolerance) ||
            (cda < -areaTolerance && cdb > areaTolerance))
    ) return true
    return (abs(abc) <= areaTolerance && c.onSegment(a, b, linearTolerance)) ||
        (abs(abd) <= areaTolerance && d.onSegment(a, b, linearTolerance)) ||
        (abs(cda) <= areaTolerance && a.onSegment(c, d, linearTolerance)) ||
        (abs(cdb) <= areaTolerance && b.onSegment(c, d, linearTolerance))
}

private fun Vec2.onSegment(a: Vec2, b: Vec2, tolerance: Double): Boolean =
    x >= minOf(a.x, b.x) - tolerance && x <= maxOf(a.x, b.x) + tolerance &&
        y >= minOf(a.y, b.y) - tolerance && y <= maxOf(a.y, b.y) + tolerance
