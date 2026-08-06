/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import org.khronos.webgl.Float32Array
import polyhedra.model.poly.*
import polyhedra.model.util.Vec3
import kotlin.math.max
import kotlin.math.min

private const val EDGE_HIT_RADIUS = 8.0
private const val VERTEX_HIT_RADIUS = 10.0
private const val SCREEN_EPSILON = 1e-9

/** CPU-side picking against the same model/projection transforms used by WebGL. */
internal class CanvasOrbitPicker(
    private val poly: Polyhedron,
    private val view: ViewContext,
    private val width: Int,
    private val height: Int,
    private val expand: Double,
    private val excludedFaces: Set<FaceKind>,
    private val animation: TransformAnimation? = null,
) {
    fun hitFace(x: Double, y: Double): FaceKind? {
        var hit: FaceKind? = null
        var nearestDepth = Double.POSITIVE_INFINITY
        for (face in poly.fs) {
            if (face.kind in excludedFaces) continue
            val projected = projectFace(face) ?: continue
            val depth = projected.depthAt(x, y) ?: continue
            if (depth < nearestDepth) {
                nearestDepth = depth
                hit = face.kind
            }
        }
        return hit
    }

    fun hitEdge(x: Double, y: Double): EdgeKind? {
        var hit: EdgeKind? = null
        var nearestDistance = EDGE_HIT_RADIUS * EDGE_HIT_RADIUS
        var nearestDepth = Double.POSITIVE_INFINITY
        for (face in poly.fs) {
            val projected = projectFace(face) ?: continue
            for (index in 0 until face.size) {
                val next = (index + 1) % face.size
                val a = projected.points[index]
                val b = projected.points[next]
                val segment = segmentHit(x, y, a, b)
                if (segment.distanceSquared > nearestDistance + SCREEN_EPSILON) continue
                if (segment.distanceSquared < nearestDistance - SCREEN_EPSILON || segment.depth < nearestDepth) {
                    nearestDistance = segment.distanceSquared
                    nearestDepth = segment.depth
                    hit = face.directedEdgeAt(index).normalizedDirection().kind
                }
            }
        }
        return hit
    }

    fun hitVertex(x: Double, y: Double): VertexKind? {
        val frontVertexIds = BooleanArray(poly.vs.size)
        for (face in poly.fs) {
            if (projectFace(face) != null) {
                for (vertex in face) frontVertexIds[vertex.id] = true
            }
        }

        var hit: VertexKind? = null
        var nearestDistance = VERTEX_HIT_RADIUS * VERTEX_HIT_RADIUS
        var nearestDepth = Double.POSITIVE_INFINITY
        for (vertex in poly.vs) {
            if (!frontVertexIds[vertex.id]) continue
            val point = projectVertex(vertex) ?: continue
            val dx = point.x - x
            val dy = point.y - y
            val distance = dx * dx + dy * dy
            if (distance > nearestDistance + SCREEN_EPSILON) continue
            if (distance < nearestDistance - SCREEN_EPSILON || point.depth < nearestDepth) {
                nearestDistance = distance
                nearestDepth = point.depth
                hit = vertex.kind
            }
        }
        return hit
    }

    internal fun project(point: Vec3, expandDirection: Vec3 = Vec3.ZERO): ScreenPoint? {
        val px = point.x + expandDirection.x * expand
        val py = point.y + expandDirection.y * expand
        val pz = point.z + expandDirection.z * expand
        val world = view.modelMatrix.transform(px, py, pz, 1.0)
        val clip = view.projectionMatrix.transform(world.x, world.y, world.z, world.w)
        if (clip.w <= SCREEN_EPSILON) return null
        val inverseW = 1.0 / clip.w
        return ScreenPoint(
            x = (clip.x * inverseW + 1.0) * width / 2.0,
            y = (1.0 - clip.y * inverseW) * height / 2.0,
            depth = clip.z * inverseW,
        )
    }

    private fun projectVertex(vertex: Vertex): ScreenPoint? {
        val previous = animation?.prevPoly?.vs?.get(vertex.id) ?: return project(vertex)
        return project(interpolate(vertex, previous))
    }

    private fun projectFace(face: Face): ProjectedFace? {
        val points = ArrayList<ScreenPoint>(face.size)
        val previousFace = animation?.prevPoly?.fs?.get(face.id)
        val expandDirection = previousFace?.let { interpolate(face, it) } ?: face
        for (index in 0 until face.size) {
            val point = previousFace?.let { interpolate(face[index], it[index]) } ?: face[index]
            points += project(point, expandDirection) ?: return null
        }
        var signedArea = 0.0
        for (index in points.indices) {
            val next = points[(index + 1) % points.size]
            signedArea += points[index].x * next.y - next.x * points[index].y
        }
        // FaceContext reverses the clockwise stored boundary when it emits
        // triangles. A rendered front face is therefore positive in the
        // Y-down canvas coordinates used here.
        if (signedArea <= SCREEN_EPSILON) return null
        return ProjectedFace(points)
    }

    private fun interpolate(target: Vec3, previous: Vec3): Vec3 {
        val targetFraction = animation?.targetFraction ?: 1.0
        val previousFraction = animation?.prevFraction ?: 0.0
        return Vec3(
            target.x * targetFraction + previous.x * previousFraction,
            target.y * targetFraction + previous.y * previousFraction,
            target.z * targetFraction + previous.z * previousFraction,
        )
    }
}

internal fun Face.directedEdgeAt(index: Int): Edge {
    val next = (index + 1) % size
    return directedEdges.first { it.a == this[index] && it.b == this[next] }
}

internal data class ScreenPoint(val x: Double, val y: Double, val depth: Double)

private data class ClipPoint(val x: Double, val y: Double, val z: Double, val w: Double)

private data class ProjectedFace(val points: List<ScreenPoint>) {
    fun depthAt(x: Double, y: Double): Double? {
        val first = points.first()
        var nearest: Double? = null
        for (index in 2 until points.size) {
            val depth = triangleDepthAt(x, y, first, points[index - 1], points[index]) ?: continue
            nearest = nearest?.let { min(it, depth) } ?: depth
        }
        return nearest
    }
}

private data class SegmentHit(val distanceSquared: Double, val depth: Double)

private fun segmentHit(x: Double, y: Double, a: ScreenPoint, b: ScreenPoint): SegmentHit {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    val fraction = if (lengthSquared <= SCREEN_EPSILON) {
        0.0
    } else {
        ((x - a.x) * dx + (y - a.y) * dy).div(lengthSquared).coerceIn(0.0, 1.0)
    }
    val nearestX = a.x + dx * fraction
    val nearestY = a.y + dy * fraction
    val offsetX = x - nearestX
    val offsetY = y - nearestY
    return SegmentHit(
        distanceSquared = offsetX * offsetX + offsetY * offsetY,
        depth = a.depth + (b.depth - a.depth) * fraction,
    )
}

private fun triangleDepthAt(
    x: Double,
    y: Double,
    a: ScreenPoint,
    b: ScreenPoint,
    c: ScreenPoint,
): Double? {
    val denominator = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y)
    if (denominator > -SCREEN_EPSILON && denominator < SCREEN_EPSILON) return null
    val wa = ((b.y - c.y) * (x - c.x) + (c.x - b.x) * (y - c.y)) / denominator
    val wb = ((c.y - a.y) * (x - c.x) + (a.x - c.x) * (y - c.y)) / denominator
    val wc = 1.0 - wa - wb
    if (min(wa, min(wb, wc)) < -SCREEN_EPSILON || max(wa, max(wb, wc)) > 1.0 + SCREEN_EPSILON) return null
    return wa * a.depth + wb * b.depth + wc * c.depth
}

private fun Float32Array.transform(x: Double, y: Double, z: Double, w: Double): ClipPoint =
    ClipPoint(
        x = valueAt(0) * x + valueAt(4) * y + valueAt(8) * z + valueAt(12) * w,
        y = valueAt(1) * x + valueAt(5) * y + valueAt(9) * z + valueAt(13) * w,
        z = valueAt(2) * x + valueAt(6) * y + valueAt(10) * z + valueAt(14) * w,
        w = valueAt(3) * x + valueAt(7) * y + valueAt(11) * z + valueAt(15) * w,
    )

private fun Float32Array.valueAt(index: Int): Double = asDynamic()[index] as Double
