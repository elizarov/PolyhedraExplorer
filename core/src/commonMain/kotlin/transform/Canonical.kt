/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core.transform

import kotlinx.coroutines.yield
import polyhedra.core.poly.*
import polyhedra.core.util.*
import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

private const val TARGET_TOLERANCE = 1e-12
private const val MAX_ITERATIONS = 100_000
private const val INITIAL_ADJUSTMENT = 0.01
private const val MAX_ADJUSTMENT = 0.5
private const val ADJUSTMENT_UP = 1.01
private const val ADJUSTMENT_DOWN = 0.995
private const val ORTHOGONALITY_ADJUSTMENT = 0.5
private const val OVERLAP_ADJUSTMENT = 0.1
private const val OVERLAP_WARMUP_ITERATIONS = 10_000
private const val POST_UNSCRAMBLE_ADJUSTMENT = 0.001
private const val ORBIT_MATCH_TOLERANCE = 1e-8

var totalIterations = 0

internal data class CanonicalOrbitStats(
    val points: Int,
    val pointOrbits: Int,
    val faces: Int,
    val faceOrbits: Int,
)

fun Polyhedron.canonical(): Polyhedron =
    runSynchronously { canonical(null) }

private data class PackingTopology(
    val faces: List<IntArray>,
    val pointFaces: List<IntArray>,
    val pointNeighbors: List<IntArray>,
)

private data class PackingSymmetry(
    val fullPointCount: Int,
    val pointOrbits: List<PackingPointOrbit>,
    val faceOrbits: List<PackingFaceOrbit>,
    val faceLocations: List<OrbitLocation>,
    val surroundingFaces: List<List<PackingFaceReference>>,
    val surroundingPoints: List<List<PackingPointReference>>,
)

private data class PackingPointOrbit(
    val sourcePointIndex: Int,
    val point: MutableVec3,
    val centroidTransform: OrbitRotationSum,
)

private data class OrbitLocation(
    val orbitIndex: Int,
    val rotation: OrbitRotation,
)

private data class PackingFaceOrbit(
    val points: List<PackingPointReference>,
    val center: MutableVec3 = MutableVec3(),
    val normal: MutableVec3 = MutableVec3(),
)

private data class PackingPointReference(
    val orbitIndex: Int,
    val rotation: OrbitRotation,
    val point: MutableVec3 = MutableVec3(),
)

private data class PackingFaceReference(
    val orbitIndex: Int,
    val rotation: OrbitRotation,
    val center: MutableVec3 = MutableVec3(),
    val normal: MutableVec3 = MutableVec3(),
)

private data class OrbitFrame(
    val radial: Vec3,
    val tangent: Vec3,
    val bitangent: Vec3,
)

private class OrbitRotation(
    val xx: Double,
    val xy: Double,
    val xz: Double,
    val yx: Double,
    val yy: Double,
    val yz: Double,
    val zx: Double,
    val zy: Double,
    val zz: Double,
) {
    constructor(source: OrbitFrame, target: OrbitFrame) : this(
        xx = target.radial.x * source.radial.x +
            target.tangent.x * source.tangent.x + target.bitangent.x * source.bitangent.x,
        xy = target.radial.x * source.radial.y +
            target.tangent.x * source.tangent.y + target.bitangent.x * source.bitangent.y,
        xz = target.radial.x * source.radial.z +
            target.tangent.x * source.tangent.z + target.bitangent.x * source.bitangent.z,
        yx = target.radial.y * source.radial.x +
            target.tangent.y * source.tangent.x + target.bitangent.y * source.bitangent.x,
        yy = target.radial.y * source.radial.y +
            target.tangent.y * source.tangent.y + target.bitangent.y * source.bitangent.y,
        yz = target.radial.y * source.radial.z +
            target.tangent.y * source.tangent.z + target.bitangent.y * source.bitangent.z,
        zx = target.radial.z * source.radial.x +
            target.tangent.z * source.tangent.x + target.bitangent.z * source.bitangent.x,
        zy = target.radial.z * source.radial.y +
            target.tangent.z * source.tangent.y + target.bitangent.z * source.bitangent.y,
        zz = target.radial.z * source.radial.z +
            target.tangent.z * source.tangent.z + target.bitangent.z * source.bitangent.z,
    )

    fun transformTo(destination: MutableVec3, vector: Vec3) {
        destination.set(
            xx * vector.x + xy * vector.y + xz * vector.z,
            yx * vector.x + yy * vector.y + yz * vector.z,
            zx * vector.x + zy * vector.y + zz * vector.z,
        )
    }

    companion object {
        val ID = OrbitRotation(
            xx = 1.0, xy = 0.0, xz = 0.0,
            yx = 0.0, yy = 1.0, yz = 0.0,
            zx = 0.0, zy = 0.0, zz = 1.0,
        )
    }
}

private class OrbitRotationSum(rotations: List<OrbitRotation>) {
    private val xx = rotations.sumOf { it.xx }
    private val xy = rotations.sumOf { it.xy }
    private val xz = rotations.sumOf { it.xz }
    private val yx = rotations.sumOf { it.yx }
    private val yy = rotations.sumOf { it.yy }
    private val yz = rotations.sumOf { it.yz }
    private val zx = rotations.sumOf { it.zx }
    private val zy = rotations.sumOf { it.zy }
    private val zz = rotations.sumOf { it.zz }

    fun transformTo(destination: MutableVec3, vector: Vec3) {
        destination.set(
            xx * vector.x + xy * vector.y + xz * vector.z,
            yx * vector.x + yy * vector.y + yz * vector.z,
            zx * vector.x + zy * vector.y + zz * vector.z,
        )
    }
}

/**
 * Finds the canonical representation through the edge-nearpoint/circle-packing
 * relaxation described by Adrian Rossiter's Antiprism implementation of the
 * Koebe-Andreev-Thurston construction.
 *
 * The conceptual processing mesh has one unit-sphere point per source edge. Its
 * faces are the two mutually orthogonal circle packings corresponding to source
 * faces and source vertices. The iterative solve keeps only one point and plane
 * per validated rotational orbit; precomputed proper rotations supply every
 * quotient incidence. Once those representatives converge, each vertex plane
 * is rotated to its symmetric copies and polar reciprocation reconstructs a
 * source-topology mesh with planar faces and unit-sphere-tangent edges.
 */
@OptIn(ExperimentalTime::class)
suspend fun Polyhedron.canonical(progress: OperationProgressContext?): Polyhedron {
    return canonicalWithFallback(progress)
}

private suspend fun Polyhedron.canonicalWithFallback(
    progress: OperationProgressContext?,
): Polyhedron = try {
    canonicalAttempt(
        progress,
        useSymmetry = true,
        useEdgeNearPoints = false,
        useTutteEmbedding = false,
    )
} catch (quotientFailure: IllegalStateException) {
    println("Canonicalization: quotient solve failed (${quotientFailure.message}); retrying full packing")
    try {
        canonicalAttempt(
            progress,
            useSymmetry = false,
            useEdgeNearPoints = false,
            useTutteEmbedding = false,
        )
    } catch (fullFailure: IllegalStateException) {
        println("Canonicalization: full solve failed (${fullFailure.message}); retrying from edge near-points")
        try {
            canonicalAttempt(
                progress,
                useSymmetry = false,
                useEdgeNearPoints = true,
                useTutteEmbedding = false,
            )
        } catch (nearPointFailure: IllegalStateException) {
            println(
                "Canonicalization: edge near-point solve failed (${nearPointFailure.message}); " +
                    "retrying from a Tutte embedding"
            )
            canonicalAttempt(
                progress,
                useSymmetry = false,
                useEdgeNearPoints = false,
                useTutteEmbedding = true,
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
private suspend fun Polyhedron.canonicalAttempt(
    progress: OperationProgressContext?,
    useSymmetry: Boolean,
    useEdgeNearPoints: Boolean,
    useTutteEmbedding: Boolean,
): Polyhedron {
    val startTime = TimeSource.Monotonic.markNow()
    val topology = packingTopology()
    val symmetry = packingSymmetry(
        topology,
        if (useTutteEmbedding) tuttePackingPoints(topology) else initialPackingPoints(useEdgeNearPoints),
        useSymmetry,
    )
    val offsets = symmetry.pointOrbits.map { MutableVec3() }
    val centroid = MutableVec3()
    val centroidContribution = MutableVec3()
    var correctingOverlaps = true

    var adjustment = INITIAL_ADJUSTMENT
    var lastMaxOffset = Double.POSITIVE_INFINITY
    var initialMaxOffset = 0.0
    var previousProgress = 0
    var lastReportTime = 0L
    var iterations = 0
    var result: Polyhedron? = null

    while (true) {
        updateOrbitFacePlanes(symmetry)
        centroid.setToZero()
        for (orbit in symmetry.pointOrbits) {
            orbit.centroidTransform.transformTo(centroidContribution, orbit.point)
            centroid += centroidContribution
        }
        centroid /= symmetry.fullPointCount

        var maxOffset = 0.0
        for (orbitIndex in symmetry.pointOrbits.indices) {
            val orbit = symmetry.pointOrbits[orbitIndex]
            val offset = offsets[orbitIndex]
            updatePackingPointOffset(
                point = orbit.point,
                surroundingFaces = symmetry.surroundingFaces[orbitIndex],
                surroundingPoints = symmetry.surroundingPoints[orbitIndex],
                pointOrbits = symmetry.pointOrbits,
                faceOrbits = symmetry.faceOrbits,
                adjustment = adjustment,
                correctOverlaps = correctingOverlaps,
                offset = offset,
            )
            offset -= centroid
            maxOffset = max(maxOffset, offset.norm)
        }

        for (orbitIndex in symmetry.pointOrbits.indices) {
            val point = symmetry.pointOrbits[orbitIndex].point
            point += offsets[orbitIndex]
            val length = point.norm
            check(length > EPS && length.isFinite()) { "Degenerate canonical packing point" }
            point /= length
        }

        iterations++
        if (correctingOverlaps && iterations == OVERLAP_WARMUP_ITERATIONS) {
            correctingOverlaps = false
            adjustment = POST_UNSCRAMBLE_ADJUSTMENT
            lastMaxOffset = Double.POSITIVE_INFINITY
        }
        if (initialMaxOffset == 0.0) initialMaxOffset = maxOffset
        if (maxOffset <= TARGET_TOLERANCE) {
            updateOrbitFacePlanes(symmetry)
            val candidate = rebuildFromPacking(symmetry).withOutwardOrientation()
            if (candidate.isCanonical()) {
                result = candidate
                break
            }
        }
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
            "${(startTime.elapsedNow().inWholeMilliseconds / 1000.0).fmtFix(3)} sec " +
            "(${symmetry.pointOrbits.size}/${symmetry.fullPointCount} point orbits, " +
            "${symmetry.faceOrbits.size}/${topology.faces.size} face orbits)"
    )
    totalIterations += iterations
    return requireNotNull(result)
}

private fun Polyhedron.withOutwardOrientation(): Polyhedron {
    val volume = signedVolume()
    check(abs(volume) > EPS && volume.isFinite()) { "Canonical reconstruction has zero signed volume" }
    if (volume > 0.0) return this
    val source = this
    return polyhedron {
        vertices(source.vs)
        for (face in source.fs) face(face.fvs.asReversed(), face.kind)
        faceKindSources(source.faceKindSources)
    }
}

private fun tuttePackingPoints(topology: PackingTopology): List<MutableVec3> {
    val outer = topology.faces.maxBy { it.size }
    val fixed = BooleanArray(topology.pointNeighbors.size)
    val x = DoubleArray(topology.pointNeighbors.size)
    val y = DoubleArray(topology.pointNeighbors.size)
    for ((index, pointIndex) in outer.withIndex()) {
        val angle = -2.0 * PI * index / outer.size
        fixed[pointIndex] = true
        x[pointIndex] = cos(angle)
        y[pointIndex] = sin(angle)
    }

    var converged = false
    var iteration = 0
    while (iteration < 100_000 && !converged) {
        var maxMovement = 0.0
        for (pointIndex in topology.pointNeighbors.indices) {
            if (fixed[pointIndex]) continue
            val neighbors = topology.pointNeighbors[pointIndex]
            var targetX = 0.0
            var targetY = 0.0
            for (neighbor in neighbors) {
                targetX += x[neighbor]
                targetY += y[neighbor]
            }
            targetX /= neighbors.size
            targetY /= neighbors.size
            maxMovement = max(maxMovement, norm(targetX - x[pointIndex], targetY - y[pointIndex]))
            x[pointIndex] = targetX
            y[pointIndex] = targetY
        }
        if (maxMovement <= 1e-13) {
            converged = true
        }
        iteration++
    }
    check(converged) { "Tutte packing initialization did not converge" }
    return x.indices.map { pointIndex ->
        val radiusSquared = x[pointIndex] * x[pointIndex] + y[pointIndex] * y[pointIndex]
        val denominator = radiusSquared + 1.0
        MutableVec3(
            2.0 * x[pointIndex] / denominator,
            2.0 * y[pointIndex] / denominator,
            (radiusSquared - 1.0) / denominator,
        )
    }
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
    val faces = vertexFaces + sourceFaces
    val pointFaces = es.map { edge ->
        intArrayOf(
            edge.a.id,
            vs.size + edge.l.id,
            edge.b.id,
            vs.size + edge.r.id,
        )
    }
    val pointNeighbors = pointFaces.mapIndexed { pointIndex, surrounding ->
        IntArray(surrounding.size) { index ->
            val first = faces[surrounding[index]]
            val second = faces[surrounding[(index + 1) % surrounding.size]]
            first.first { candidate -> candidate != pointIndex && candidate in second }
        }
    }
    return PackingTopology(faces, pointFaces, pointNeighbors)
}

private fun edgeKey(a: Int, b: Int): Long {
    val low = min(a, b)
    val high = max(a, b)
    return (low.toLong() shl 32) or (high.toLong() and 0xffffffffL)
}

private fun Polyhedron.initialPackingPoints(
    useEdgeNearPoints: Boolean = false,
): List<MutableVec3> {
    val points = es.map { edge ->
        if (useEdgeNearPoints) {
            MutableVec3(edge.tangentPoint())
        } else {
            MutableVec3(
                (edge.a.x + edge.b.x) / 2,
                (edge.a.y + edge.b.y) / 2,
                (edge.a.z + edge.b.z) / 2,
            )
        }
    }
    val centroid = MutableVec3()
    for (point in points) centroid += point
    centroid /= points.size
    for (point in points) point -= centroid
    return points
}

internal fun Polyhedron.canonicalOrbitStats(): CanonicalOrbitStats {
    val topology = packingTopology()
    val symmetry = packingSymmetry(topology, initialPackingPoints(), useSymmetry = true)
    return CanonicalOrbitStats(
        points = es.size,
        pointOrbits = symmetry.pointOrbits.size,
        faces = topology.faces.size,
        faceOrbits = symmetry.faceOrbits.size,
    )
}

private fun Polyhedron.packingSymmetry(
    topology: PackingTopology,
    initialPoints: List<MutableVec3>,
    useSymmetry: Boolean,
): PackingSymmetry {
    val pointLocations = arrayOfNulls<OrbitLocation>(initialPoints.size)
    val pointOrbits = ArrayList<PackingPointOrbit>()
    val pointGroups = es.indices.groupBy { pointIndex ->
        if (useSymmetry) 0 to es[pointIndex].kind else 1 to pointIndex
    }

    for (group in pointGroups.values) {
        val remaining = group.toMutableList()
        while (remaining.isNotEmpty()) {
            val representative = remaining.removeAt(0)
            val representativeFrame = packingPointFrame(representative, initialPoints)
            val orbitIndex = pointOrbits.size
            val rotations = ArrayList<OrbitRotation>()
            rotations += OrbitRotation.ID
            pointLocations[representative] = OrbitLocation(orbitIndex, OrbitRotation.ID)

            if (representativeFrame != null) {
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val pointIndex = iterator.next()
                    val rotation = packingPointRotationOrNull(
                        representative,
                        pointIndex,
                        representativeFrame,
                        topology,
                        initialPoints,
                    ) ?: continue
                    rotations += rotation
                    pointLocations[pointIndex] = OrbitLocation(orbitIndex, rotation)
                    iterator.remove()
                }
            }

            pointOrbits += PackingPointOrbit(
                sourcePointIndex = representative,
                point = MutableVec3(initialPoints[representative]),
                centroidTransform = OrbitRotationSum(rotations),
            )
        }
    }

    val initialFaceCenters = topology.faces.map { MutableVec3() }
    val initialFaceNormals = topology.faces.map { MutableVec3() }
    updateFacePlanes(initialPoints, topology.faces, initialFaceCenters, initialFaceNormals)

    val faceLocations = arrayOfNulls<OrbitLocation>(topology.faces.size)
    val faceRepresentatives = ArrayList<Int>()
    val faceGroups = topology.faces.indices.groupBy { faceIndex ->
        if (!useSymmetry) return@groupBy faceIndex to faceIndex
        if (faceIndex < vs.size) {
            0 to vs[faceIndex].kind.id
        } else {
            1 to fs[faceIndex - vs.size].kind.id
        }
    }
    for (group in faceGroups.values) {
        val remaining = group.toMutableList()
        while (remaining.isNotEmpty()) {
            val representative = remaining.removeAt(0)
            val representativeFrame = packingFaceFrame(
                representative,
                topology,
                initialPoints,
                initialFaceCenters,
            )
            val orbitIndex = faceRepresentatives.size
            faceRepresentatives += representative
            faceLocations[representative] = OrbitLocation(orbitIndex, OrbitRotation.ID)

            if (representativeFrame != null) {
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val faceIndex = iterator.next()
                    val rotation = packingFaceRotationOrNull(
                        representative,
                        faceIndex,
                        representativeFrame,
                        topology,
                        initialPoints,
                        initialFaceCenters,
                    ) ?: continue
                    faceLocations[faceIndex] = OrbitLocation(orbitIndex, rotation)
                    iterator.remove()
                }
            }
        }
    }

    val resolvedPointLocations = pointLocations.map { requireNotNull(it) }
    val resolvedFaceLocations = faceLocations.map { requireNotNull(it) }
    val faceOrbits = faceRepresentatives.map { faceIndex ->
        PackingFaceOrbit(
            points = topology.faces[faceIndex].map { pointIndex ->
                val location = resolvedPointLocations[pointIndex]
                PackingPointReference(location.orbitIndex, location.rotation)
            },
        )
    }
    val surroundingFaces = pointOrbits.map { orbit ->
        topology.pointFaces[orbit.sourcePointIndex].map { faceIndex ->
            val location = resolvedFaceLocations[faceIndex]
            PackingFaceReference(location.orbitIndex, location.rotation)
        }
    }
    val surroundingPoints = pointOrbits.map { orbit ->
        topology.pointNeighbors[orbit.sourcePointIndex].map { pointIndex ->
            val location = resolvedPointLocations[pointIndex]
            PackingPointReference(location.orbitIndex, location.rotation)
        }
    }

    return PackingSymmetry(
        fullPointCount = initialPoints.size,
        pointOrbits = pointOrbits,
        faceOrbits = faceOrbits,
        faceLocations = resolvedFaceLocations,
        surroundingFaces = surroundingFaces,
        surroundingPoints = surroundingPoints,
    )
}

private fun Polyhedron.packingPointFrame(
    pointIndex: Int,
    points: List<MutableVec3>,
): OrbitFrame? = packingFrame(points[pointIndex], es[pointIndex].vec)

private fun Polyhedron.packingPointRotationOrNull(
    representative: Int,
    pointIndex: Int,
    representativeFrame: OrbitFrame,
    topology: PackingTopology,
    points: List<MutableVec3>,
): OrbitRotation? {
    val targetEdgeVector = es[pointIndex].vec
    for (direction in listOf(targetEdgeVector, targetEdgeVector * -1.0)) {
        val targetFrame = packingFrame(points[pointIndex], direction) ?: continue
        val rotation = OrbitRotation(representativeFrame, targetFrame)
        if (packingPointNeighborhoodMatches(representative, pointIndex, rotation, topology, points)) {
            return rotation
        }
    }
    return null
}

private fun Polyhedron.packingFaceFrame(
    faceIndex: Int,
    topology: PackingTopology,
    points: List<MutableVec3>,
    faceCenters: List<MutableVec3>,
): OrbitFrame? {
    val face = topology.faces[faceIndex]
    val anchorIndex = face.minWithOrNull(compareBy { es[it].kind }) ?: return null
    return packingFrame(faceCenters[faceIndex], points[anchorIndex] - faceCenters[faceIndex])
}

private fun Polyhedron.packingFaceRotationOrNull(
    representative: Int,
    faceIndex: Int,
    representativeFrame: OrbitFrame,
    topology: PackingTopology,
    points: List<MutableVec3>,
    faceCenters: List<MutableVec3>,
): OrbitRotation? {
    val representativeAnchor = topology.faces[representative].minWithOrNull(compareBy { es[it].kind })
        ?: return null
    val anchorKind = es[representativeAnchor].kind
    for (targetAnchor in topology.faces[faceIndex]) {
        if (es[targetAnchor].kind != anchorKind) continue
        val targetFrame = packingFrame(
            faceCenters[faceIndex],
            points[targetAnchor] - faceCenters[faceIndex],
        ) ?: continue
        val rotation = OrbitRotation(representativeFrame, targetFrame)
        if (packingFacesMatch(
                topology.faces[representative],
                topology.faces[faceIndex],
                rotation,
                points,
            )
        ) return rotation
    }
    return null
}

private fun packingFrame(radialVector: Vec3, tangentVector: Vec3): OrbitFrame? {
    val radialLength = radialVector.norm
    if (radialLength <= EPS || !radialLength.isFinite()) return null
    val radial = radialVector / radialLength
    val radialProjection = tangentVector * radial
    val tangent = tangentVector - radial * radialProjection
    val tangentLength = tangent.norm
    if (tangentLength <= EPS || !tangentLength.isFinite()) return null
    val tangentUnit = tangent / tangentLength
    return OrbitFrame(radial, tangentUnit, radial cross tangentUnit)
}

private fun packingPointNeighborhoodMatches(
    representative: Int,
    pointIndex: Int,
    rotation: OrbitRotation,
    topology: PackingTopology,
    points: List<MutableVec3>,
): Boolean {
    val transformed = MutableVec3()
    rotation.transformTo(transformed, points[representative])
    if (!transformed.closeTo(points[pointIndex])) return false

    val representativeFaces = topology.pointFaces[representative]
    val pointFaces = topology.pointFaces[pointIndex]
    val matched = BooleanArray(pointFaces.size)
    for ((representativeIndex, representativeFace) in representativeFaces.withIndex()) {
        var matchIndex = -1
        for (index in pointFaces.indices) {
            val targetFace = pointFaces[index]
            if (matched[index]) continue
            // Even slots are vertex-derived faces and odd slots are
            // source-face-derived; rotations must preserve the two packings.
            if (index % 2 != representativeIndex % 2) continue
            if (packingFacesMatch(
                    topology.faces[representativeFace],
                    topology.faces[targetFace],
                    rotation,
                    points,
                )
            ) {
                matchIndex = index
                break
            }
        }
        if (matchIndex < 0) return false
        matched[matchIndex] = true
    }
    return true
}

private fun packingFacesMatch(
    source: IntArray,
    target: IntArray,
    rotation: OrbitRotation,
    points: List<MutableVec3>,
): Boolean {
    if (source.size != target.size) return false
    val matched = BooleanArray(target.size)
    val transformed = MutableVec3()
    for (sourcePoint in source) {
        rotation.transformTo(transformed, points[sourcePoint])
        var matchIndex = -1
        for (index in target.indices) {
            if (!matched[index] && transformed.closeTo(points[target[index]])) {
                matchIndex = index
                break
            }
        }
        if (matchIndex < 0) return false
        matched[matchIndex] = true
    }
    return true
}

private fun Vec3.closeTo(other: Vec3): Boolean {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    val scale = max(1.0, max(norm, other.norm))
    return norm(dx, dy, dz) <= ORBIT_MATCH_TOLERANCE * scale
}

private fun updateOrbitFacePlanes(symmetry: PackingSymmetry) {
    for (face in symmetry.faceOrbits) {
        val center = face.center
        val normal = face.normal
        center.setToZero()
        normal.setToZero()
        for (pointRef in face.points) {
            pointRef.rotation.transformTo(pointRef.point, symmetry.pointOrbits[pointRef.orbitIndex].point)
            center += pointRef.point
        }
        center /= face.points.size
        for (index in face.points.indices) {
            crossCenteredAddTo(
                normal,
                face.points[index].point,
                face.points[(index + 1) % face.points.size].point,
                center,
            )
        }
        val length = normal.norm
        check(length > EPS && length.isFinite()) { "Degenerate canonical packing face" }
        normal /= length
        if (normal * center < 0.0) normal *= -1.0
    }
}

private fun updatePackingPointOffset(
    point: Vec3,
    surroundingFaces: List<PackingFaceReference>,
    surroundingPoints: List<PackingPointReference>,
    pointOrbits: List<PackingPointOrbit>,
    faceOrbits: List<PackingFaceOrbit>,
    adjustment: Double,
    correctOverlaps: Boolean,
    offset: MutableVec3,
) {
    offset.setToZero()
    for (faceRef in surroundingFaces) {
        val face = faceOrbits[faceRef.orbitIndex]
        faceRef.rotation.transformTo(faceRef.center, face.center)
        faceRef.rotation.transformTo(faceRef.normal, face.normal)
        val distance =
            faceRef.normal.x * (faceRef.center.x - point.x) +
                faceRef.normal.y * (faceRef.center.y - point.y) +
                faceRef.normal.z * (faceRef.center.z - point.z)
        offset.x += point.x + faceRef.normal.x * distance
        offset.y += point.y + faceRef.normal.y * distance
        offset.z += point.z + faceRef.normal.z * distance
    }
    offset.x = (offset.x / surroundingFaces.size - point.x) * adjustment
    offset.y = (offset.y / surroundingFaces.size - point.y) * adjustment
    offset.z = (offset.z / surroundingFaces.size - point.z) * adjustment

    for (pairIndex in 0 until 2) {
        val first = surroundingFaces[pairIndex].normal
        val opposite = surroundingFaces[pairIndex + 2].normal
        val nx = opposite.y * first.z - opposite.z * first.y
        val ny = opposite.z * first.x - opposite.x * first.z
        val nz = opposite.x * first.y - opposite.y * first.x
        val length = norm(nx, ny, nz)
        if (length <= EPS || !length.isFinite()) continue
        val ux = nx / length
        val uy = ny / length
        val uz = nz / length
        val distance = point.x * ux + point.y * uy + point.z * uz
        val factor = adjustment * ORTHOGONALITY_ADJUSTMENT * distance
        offset.x -= ux * factor
        offset.y -= uy * factor
        offset.z -= uz * factor
    }

    if (!correctOverlaps) return
    for (pointRef in surroundingPoints) {
        pointRef.rotation.transformTo(pointRef.point, pointOrbits[pointRef.orbitIndex].point)
    }
    for (index in surroundingPoints.indices) {
        val first = surroundingPoints[index].point
        val second = surroundingPoints[(index + 1) % surroundingPoints.size].point
        val triple =
            point.x * (first.y * second.z - first.z * second.y) +
                point.y * (first.z * second.x - first.x * second.z) +
                point.z * (first.x * second.y - first.y * second.x)
        if (triple <= 0.0) continue
        var x = 0.0
        var y = 0.0
        var z = 0.0
        for (pointRef in surroundingPoints) {
            x += pointRef.point.x
            y += pointRef.point.y
            z += pointRef.point.z
        }
        offset.x += (x / surroundingPoints.size - point.x) * OVERLAP_ADJUSTMENT
        offset.y += (y / surroundingPoints.size - point.y) * OVERLAP_ADJUSTMENT
        offset.z += (z / surroundingPoints.size - point.z) * OVERLAP_ADJUSTMENT
        return
    }
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

private fun Polyhedron.rebuildFromPacking(symmetry: PackingSymmetry): Polyhedron =
    polyhedron(mergeIndistinguishableKinds = true) {
        val center = MutableVec3()
        val normal = MutableVec3()
        for (vertex in vs) {
            val location = symmetry.faceLocations[vertex.id]
            val face = symmetry.faceOrbits[location.orbitIndex]
            location.rotation.transformTo(center, face.center)
            location.rotation.transformTo(normal, face.normal)
            val distance = normal * center
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
