/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.transform

import kotlinx.coroutines.yield
import polyhedra.common.poly.*
import polyhedra.common.util.*
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

private const val TARGET_TOLERANCE = 1e-12
private const val MAX_ITERATIONS = 100_000
private const val INITIAL_ADJUSTMENT = 0.01
private const val MAX_ADJUSTMENT = 0.5
private const val ADJUSTMENT_UP = 1.01
private const val ADJUSTMENT_DOWN = 0.995
private const val ORTHOGONALITY_ADJUSTMENT = 0.5

var totalIterations = 0

fun Polyhedron.canonical(): Polyhedron =
    runSynchronously { canonical(null) }

private data class PackingTopology(
    val faces: List<IntArray>,
    val pointFaces: List<IntArray>,
)

/**
 * Finds the canonical representation through the edge-nearpoint/circle-packing
 * relaxation described by Adrian Rossiter's Antiprism implementation of the
 * Koebe-Andreev-Thurston construction.
 *
 * The processing mesh has one unit-sphere point per source edge. Its faces are
 * the two mutually orthogonal circle packings corresponding to source faces and
 * source vertices. Once those points converge, polar reciprocation reconstructs
 * a source-topology mesh with planar faces and unit-sphere-tangent edges.
 */
@OptIn(ExperimentalTime::class)
suspend fun Polyhedron.canonical(progress: OperationProgressContext?): Polyhedron {
    val startTime = TimeSource.Monotonic.markNow()
    val topology = packingTopology()
    val points = initialPackingPoints()
    val faceCenters = topology.faces.map { MutableVec3() }
    val faceNormals = topology.faces.map { MutableVec3() }
    val offsets = points.map { MutableVec3() }
    val centroid = MutableVec3()

    var adjustment = INITIAL_ADJUSTMENT
    var lastMaxOffset = Double.POSITIVE_INFINITY
    var initialMaxOffset = 0.0
    var previousProgress = 0
    var lastReportTime = 0L
    var iterations = 0

    while (true) {
        updateFacePlanes(points, topology.faces, faceCenters, faceNormals)
        centroid.setToZero()
        for (point in points) centroid += point
        centroid /= points.size

        var maxOffset = 0.0
        for (pointIndex in points.indices) {
            val point = points[pointIndex]
            val offset = offsets[pointIndex]
            val surroundingFaces = topology.pointFaces[pointIndex]
            offset.setToZero()

            // Pull the point toward the centroid of its projections onto the
            // four surrounding primal/dual circle planes.
            for (faceIndex in surroundingFaces) {
                val normal = faceNormals[faceIndex]
                val center = faceCenters[faceIndex]
                val distance =
                    normal.x * (center.x - point.x) +
                        normal.y * (center.y - point.y) +
                        normal.z * (center.z - point.z)
                offset.x += point.x + normal.x * distance
                offset.y += point.y + normal.y * distance
                offset.z += point.z + normal.z * distance
            }
            offset.x = (offset.x / surroundingFaces.size - point.x) * adjustment - centroid.x
            offset.y = (offset.y / surroundingFaces.size - point.y) * adjustment - centroid.y
            offset.z = (offset.z / surroundingFaces.size - point.z) * adjustment - centroid.z

            // Opposite surrounding faces belong to the same circle packing.
            // Their normals define a plane through the origin on which the
            // shared edge point must lie for mutual tangency/orthogonality.
            for (pairIndex in 0 until 2) {
                val first = faceNormals[surroundingFaces[pairIndex]]
                val opposite = faceNormals[surroundingFaces[pairIndex + 2]]
                val nx = opposite.y * first.z - opposite.z * first.y
                val ny = opposite.z * first.x - opposite.x * first.z
                val nz = opposite.x * first.y - opposite.y * first.x
                val length = norm(nx, ny, nz)
                check(length > EPS && length.isFinite()) { "Degenerate canonical packing plane" }
                val ux = nx / length
                val uy = ny / length
                val uz = nz / length
                val distance = point.x * ux + point.y * uy + point.z * uz
                val factor = adjustment * ORTHOGONALITY_ADJUSTMENT * distance
                offset.x -= ux * factor
                offset.y -= uy * factor
                offset.z -= uz * factor
            }

            maxOffset = max(maxOffset, offset.norm)
        }

        for (pointIndex in points.indices) {
            val point = points[pointIndex]
            point += offsets[pointIndex]
            val length = point.norm
            check(length > EPS && length.isFinite()) { "Degenerate canonical packing point" }
            point /= length
        }

        iterations++
        if (initialMaxOffset == 0.0) initialMaxOffset = maxOffset
        if (maxOffset <= TARGET_TOLERANCE) break
        check(iterations < MAX_ITERATIONS) {
            "Canonicalization did not converge after $iterations iterations (offset=$maxOffset)"
        }

        adjustment = if (maxOffset < lastMaxOffset) {
            min(MAX_ADJUSTMENT, adjustment * ADJUSTMENT_UP)
        } else {
            adjustment * ADJUSTMENT_DOWN
        }
        lastMaxOffset = maxOffset

        val currentTime = startTime.elapsedNow().inWholeMilliseconds / 100
        if (currentTime <= lastReportTime) continue
        lastReportTime = currentTime
        val done = convergenceProgress(initialMaxOffset, maxOffset)
        if (currentTime % 10 == 0L) {
            println(
                "Canonicalization: at $iterations iterations, log offset = ${log10(maxOffset).fmt}, " +
                    "done = $done%"
            )
        }
        if (done > previousProgress) {
            previousProgress = done
            progress?.reportProgress(done)
        }
        yield()
    }

    println(
        "Canonicalization: done $iterations iterations in " +
            "${(startTime.elapsedNow().inWholeMilliseconds / 1000.0).fmtFix(3)} sec"
    )
    totalIterations += iterations
    updateFacePlanes(points, topology.faces, faceCenters, faceNormals)
    return rebuildFromPacking(faceCenters, faceNormals)
}

private fun convergenceProgress(initialOffset: Double, currentOffset: Double): Int {
    if (currentOffset <= TARGET_TOLERANCE) return 99
    if (initialOffset <= TARGET_TOLERANCE || !currentOffset.isFinite()) return 1
    val denominator = log10(initialOffset / TARGET_TOLERANCE)
    if (denominator <= 0.0) return 1
    return (100 * log10(initialOffset / currentOffset) / denominator).toInt().coerceIn(1, 99)
}

private fun Polyhedron.packingTopology(): PackingTopology {
    val edgeIndices = HashMap<Long, Int>(es.size)
    for ((index, edge) in es.withIndex()) {
        edgeIndices[edgeKey(edge.a.id, edge.b.id)] = index
    }
    fun Edge.index(): Int = edgeIndices.getValue(edgeKey(a.id, b.id))

    // Vertex-derived faces come first because they reciprocate directly back
    // into source vertices after the packing converges.
    val vertexFaces = vs.map { vertex ->
        vertex.directedEdges.map { it.index() }.toIntArray()
    }
    val sourceFaces = fs.map { face ->
        face.directedEdges.map { it.index() }.toIntArray()
    }
    val pointFaces = es.map { edge ->
        intArrayOf(
            edge.a.id,
            vs.size + edge.l.id,
            edge.b.id,
            vs.size + edge.r.id,
        )
    }
    return PackingTopology(vertexFaces + sourceFaces, pointFaces)
}

private fun edgeKey(a: Int, b: Int): Long {
    val low = min(a, b)
    val high = max(a, b)
    return (low.toLong() shl 32) or (high.toLong() and 0xffffffffL)
}

private fun Polyhedron.initialPackingPoints(): List<MutableVec3> {
    val points = es.map { edge ->
        MutableVec3(
            (edge.a.x + edge.b.x) / 2,
            (edge.a.y + edge.b.y) / 2,
            (edge.a.z + edge.b.z) / 2,
        )
    }
    val centroid = MutableVec3()
    for (point in points) centroid += point
    centroid /= points.size
    for (point in points) point -= centroid
    return points
}

private fun updateFacePlanes(
    points: List<MutableVec3>,
    faces: List<IntArray>,
    centers: List<MutableVec3>,
    normals: List<MutableVec3>,
) {
    for (faceIndex in faces.indices) {
        val face = faces[faceIndex]
        val center = centers[faceIndex]
        val normal = normals[faceIndex]
        center.setToZero()
        normal.setToZero()
        for (pointIndex in face) center += points[pointIndex]
        center /= face.size
        for (index in face.indices) {
            crossCenteredAddTo(
                normal,
                points[face[index]],
                points[face[(index + 1) % face.size]],
                center,
            )
        }
        val length = normal.norm
        check(length > EPS && length.isFinite()) { "Degenerate canonical packing face" }
        normal /= length
        if (normal * center < 0.0) normal *= -1.0
    }
}

private fun Polyhedron.rebuildFromPacking(
    faceCenters: List<MutableVec3>,
    faceNormals: List<MutableVec3>,
): Polyhedron = polyhedron(mergeIndistinguishableKinds = true) {
    for (vertex in vs) {
        val faceIndex = vertex.id
        val normal = faceNormals[faceIndex]
        val distance = normal * faceCenters[faceIndex]
        check(abs(distance) > EPS && distance.isFinite()) { "Degenerate canonical reciprocal face" }
        vertex(MutableVec3(normal.x / distance, normal.y / distance, normal.z / distance), vertex.kind)
    }
    faces(fs)
}

fun Polyhedron.isCanonical(): Boolean {
    // The canonical origin is the centroid of edge tangency points.
    val center = MutableVec3()
    for (edge in es) center += edge.tangentPoint()
    center /= es.size
    if (!(center approx Vec3.ZERO)) return false
    if (fs.any { !it.isPlanar }) return false

    var minDistance = Double.POSITIVE_INFINITY
    var maxDistance = 0.0
    for (edge in es) {
        val distance = edge.tangentDistance()
        if (distance < minDistance) minDistance = distance
        if (distance > maxDistance) maxDistance = distance
    }
    return maxDistance approx minDistance
}
