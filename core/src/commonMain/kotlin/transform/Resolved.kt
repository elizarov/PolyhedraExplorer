package polyhedra.core.transform

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import polyhedra.core.poly.analyzeGeometry
import polyhedra.core.poly.polyhedron
import polyhedra.core.poly.validateProperGeometry
import polyhedra.core.util.OperationProgressContext
import polyhedra.core.util.runSynchronously
import polyhedra.model.api.MAX_POLYHEDRON_EDGES
import polyhedra.model.api.CoreGeometryAnalysis
import polyhedra.model.api.PolyhedronContract
import polyhedra.model.api.CoreIssueCode
import polyhedra.model.api.ResolvedElementProvenance
import polyhedra.model.api.ResolvedTopologyProvenance
import polyhedra.model.api.SourceSegmentPoint
import polyhedra.model.api.TransformId
import polyhedra.model.api.TransformOperation
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.VertexKind
import polyhedra.model.util.EPS
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.plus
import polyhedra.model.util.times
import polyhedra.model.util.unit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor

/** Selects the embedded zero/nonzero-winding interface of an immersed source surface. */
@Serializable
class Resolved : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.Resolved)
    @Transient
    override val support = TransformSupport(
        topologyRequirement = TopologyRequirement.PlanarArrangement,
        outputPolicy = TransformOutputPolicy.EmbeddedBoundary,
    )

    override fun transform(poly: Polyhedron): Polyhedron = runSynchronously { poly.resolved(null) }

    @Transient
    override val asyncTransform: AsyncTransform = { poly, progress -> poly.resolved(progress) }

    override fun isIdentityTransform(poly: Polyhedron): Boolean =
        poly.analyzeGeometry().strongestContract == PolyhedronContract.EmbeddedBoundary
}

fun Polyhedron.resolved(): Polyhedron = runSynchronously { resolved(null) }

/**
 * Corefines presentation triangles along their actual pairwise intersection lines, then retains
 * only fragments separating zero from nonzero generalized winding. The triangle boundary is the
 * authoritative first phase; safe polygon merging is deliberately a separate topology phase.
 */
suspend fun Polyhedron.resolved(progress: OperationProgressContext?): Polyhedron =
    resolvedSurface(
        progress,
        validateSource = true,
        knownSourceAnalysis = null,
        retainProvenance = true,
        maximumEdges = MAX_POLYHEDRON_EDGES,
    )

/** Reuses a classification already computed by a candidate-construction pipeline. */
internal suspend fun Polyhedron.resolved(
    progress: OperationProgressContext?,
    knownSourceAnalysis: CoreGeometryAnalysis,
): Polyhedron = resolvedSurface(
    progress,
    validateSource = true,
    knownSourceAnalysis = knownSourceAnalysis,
    retainProvenance = true,
    maximumEdges = MAX_POLYHEDRON_EDGES,
)

internal data class TriangleSoupTriangle(
    val a: Vec3,
    val b: Vec3,
    val c: Vec3,
    val sourceFaceId: Int,
    val solidId: Int = -1,
)

internal data class TriangleSoupBoundary(
    val positions: List<Vec3>,
    /** Counter-clockwise triangles viewed from outside the retained solid. */
    val triangles: List<List<Int>>,
)

/** Resolves presentation triangles without requiring an abstract manifold before arrangement. */
internal suspend fun resolvedTriangleSoup(
    triangles: List<TriangleSoupTriangle>,
    progress: OperationProgressContext?,
    maximumEdges: Int,
    toleranceFloor: Double = 0.0,
): TriangleSoupBoundary {
    val points = triangles.flatMap { triangle -> listOf(triangle.a, triangle.b, triangle.c) }
    val radius = points.maxOfOrNull(Vec3::norm) ?: 0.0
    val source = triangles.map { triangle ->
        SourceTriangle(triangle.sourceFaceId, triangle.a, triangle.b, triangle.c, solidId = triangle.solidId)
    }
    val boundary = resolveBoundary(
        source,
        radius,
        progress,
        maximumEdges,
        mergeFaces = false,
        allowCoplanarOverlap = true,
        toleranceFloor = toleranceFloor,
    )
    require(boundary.faceVertexIds.all { face -> face.size == 3 })
    return TriangleSoupBoundary(boundary.positions, boundary.faceVertexIds)
}

private suspend fun Polyhedron.resolvedSurface(
    progress: OperationProgressContext?,
    validateSource: Boolean,
    knownSourceAnalysis: CoreGeometryAnalysis?,
    retainProvenance: Boolean,
    maximumEdges: Int,
): Polyhedron {
    if (validateSource) {
        val analysis = knownSourceAnalysis ?: analyzeGeometry()
        if (analysis.strongestContract == PolyhedronContract.EmbeddedBoundary) return this
    }

    return resolveSurface(
        source = resolvedSurfaceTriangles(),
        radius = circumradius,
        progress = progress,
        maximumEdges = maximumEdges,
        provenanceSource = this.takeIf { retainProvenance },
        mergeFaces = true,
        allowCoplanarOverlap = isCompound,
        toleranceFloor = 0.0,
        validateResult = true,
    )
}

private suspend fun resolveSurface(
    source: List<SourceTriangle>,
    radius: Double,
    progress: OperationProgressContext?,
    maximumEdges: Int,
    provenanceSource: Polyhedron?,
    mergeFaces: Boolean,
    allowCoplanarOverlap: Boolean,
    toleranceFloor: Double,
    validateResult: Boolean,
): Polyhedron {
    val boundary = resolveBoundary(
        source,
        radius,
        progress,
        maximumEdges,
        mergeFaces,
        allowCoplanarOverlap,
        toleranceFloor,
    )
    val tolerance = maxOf(EPS * radius * 32.0, 1e-12 * radius, toleranceFloor)
    val topologyProvenance = provenanceSource?.let { boundary.sourceProvenance(it, tolerance) }
    val result = polyhedron(mergeIndistinguishableKinds = true) {
        boundary.positions.forEachIndexed { index, position -> vertex(position, VertexKind(index)) }
        // Working fragments are counter-clockwise from outside; Polyhedron stores clockwise faces.
        boundary.faceVertexIds.forEachIndexed { index, polygon ->
            face(polygon.asReversed(), FaceKind(index))
        }
        resolvedTopologyProvenance(topologyProvenance)
    }
    if (validateResult) result.validateProperGeometry()
    progress?.reportProgress(100)
    return result
}

private suspend fun resolveBoundary(
    source: List<SourceTriangle>,
    radius: Double,
    progress: OperationProgressContext?,
    maximumEdges: Int,
    mergeFaces: Boolean,
    allowCoplanarOverlap: Boolean,
    toleranceFloor: Double,
): PolygonBoundary {
    require(radius.isFinite() && radius > 0.0) { "Resolved requires a finite nonzero circumradius" }
    val tolerance = maxOf(EPS * radius * 32.0, 1e-12 * radius, toleranceFloor)
    require(source.isNotEmpty()) { "Resolved requires presentation triangles" }
    val windingClassifier = WindingClassifier(source, tolerance)

    val cuts = List(source.size) { mutableListOf<CutLine>() }
    val coplanarParents = IntArray(source.size) { it }
    fun coplanarRoot(index: Int): Int {
        var current = index
        while (coplanarParents[current] != current) {
            coplanarParents[current] = coplanarParents[coplanarParents[current]]
            current = coplanarParents[current]
        }
        return current
    }
    val ordered = source.indices.sortedWith(compareBy({ source[it].minX }, { it }))
    for (firstPosition in ordered.indices) {
        val firstIndex = ordered[firstPosition]
        val first = source[firstIndex]
        for (secondPosition in (firstPosition + 1) until ordered.size) {
            val secondIndex = ordered[secondPosition]
            val second = source[secondIndex]
            if (second.minX > first.maxX + tolerance) break
            if (!first.boundsOverlap(second, tolerance) || first.sourceFaceId == second.sourceFaceId) continue
            if (first.hasPositiveAreaCoplanarOverlap(second, tolerance)) {
                if (!allowCoplanarOverlap) {
                    throw TransformApplicabilityException(
                        CoreIssueCode.TransformNotApplicable,
                        "Resolved does not support coincident or positive-area overlapping source faces",
                    )
                }
                // STL pieces can overlap in their own plane (for example a visible face and a
                // neighboring rim). Corefine both triangles by every opposing edge so winding is
                // sampled on complete coplanar arrangement cells, never on a partially covered
                // triangle.
                second.edges.forEach { edge -> cuts[firstIndex].addCut(first, edge, tolerance) }
                first.edges.forEach { edge -> cuts[secondIndex].addCut(second, edge, tolerance) }
                coplanarParents[coplanarRoot(firstIndex)] = coplanarRoot(secondIndex)
                continue
            }
            triangleIntersectionSegment(first, second, tolerance)?.let { segment ->
                cuts[firstIndex].addCut(first, segment, tolerance)
                cuts[secondIndex].addCut(second, segment, tolerance)
            }
        }
        progress?.reportProgress(35 * (firstPosition + 1) / ordered.size)
    }

    // Every overlapping coplanar source must see the same arrangement lines, including cuts
    // introduced by third-party faces. Otherwise identical material gets incompatible partitions.
    for (group in source.indices.groupBy(::coplanarRoot).values) if (group.size > 1) {
        val combined = arrayListOf<CutLine>()
        for (index in group) for (cut in cuts[index]) if (combined.none {
            (it.normal - cut.normal).norm <= EPS * 32.0 && abs(it.distance - cut.distance) <= tolerance
        }) combined += cut
        group.forEach { cuts[it].clear(); cuts[it].addAll(combined) }
    }
    val fragments = ArrayList<OrientedTriangle>()
    for ((index, triangle) in source.withIndex()) {
        val polygons = triangle.splitBy(cuts[index], tolerance)
        for (rawPolygon in polygons) {
            // A canonical fan root makes coincident arrangement cells triangulate identically.
            val first = rawPolygon.indices.minWith(compareBy(
                { kotlin.math.round(rawPolygon[it].x / tolerance).toLong() },
                { kotlin.math.round(rawPolygon[it].y / tolerance).toLong() },
                { kotlin.math.round(rawPolygon[it].z / tolerance).toLong() },
            ))
            val polygon = List(rawPolygon.size) { rawPolygon[(first + it) % rawPolygon.size] }
            for (fanIndex in 1 until polygon.lastIndex) {
                val fragment = OrientedTriangle(
                    polygon[0],
                    polygon[fanIndex],
                    polygon[fanIndex + 1],
                    triangle.sourceFaceId,
                )
                if (fragment.area2 <= tolerance * tolerance) continue
                val offset = maxOf(tolerance * 8.0, fragment.shortestEdge * 1e-7)
                val center = fragment.center
                val normal = fragment.normal
                val minusInside = windingClassifier.hasNonzeroWinding(center - normal * offset)
                val plusInside = windingClassifier.hasNonzeroWinding(center + normal * offset)
                when {
                    minusInside && !plusInside -> fragments += fragment
                    plusInside && !minusInside -> fragments += fragment.reversed()
                }
            }
        }
        progress?.reportProgress(35 + 45 * (index + 1) / source.size)
    }
    if (fragments.isEmpty()) {
        throw TransformApplicabilityException(
            CoreIssueCode.TransformNotApplicable,
            "Resolved found no zero/nonzero-winding boundary",
        )
    }

    val conformedFragments = fragments.conformEdges(tolerance)
    progress?.reportProgress(82)
    val triangulatedBoundary = buildTriangleBoundary(conformedFragments, tolerance)
    progress?.reportProgress(84)
    val boundary = if (mergeFaces) {
        triangulatedBoundary.mergeCoplanarFaces(tolerance)
    } else {
        triangulatedBoundary.asTrianglePolygons()
    }
    progress?.reportProgress(88)
    val boundaryEdges = boundary.edgeKeys()
    val incidentFaces = HashMap<IndexEdge, MutableList<Int>>()
    boundary.faceVertexIds.forEachIndexed { faceIndex, face ->
        face.indices.forEach { index ->
            incidentFaces.getOrPut(indexEdge(face[index], face[(index + 1) % face.size]), ::arrayListOf) += faceIndex
        }
    }
    incidentFaces.entries.firstOrNull { (_, faces) -> faces.size != 2 }?.let { (edge, faces) ->
        throw TransformApplicabilityException(
            CoreIssueCode.TransformNotApplicable,
            "Resolved boundary edge ${edge.a}-${edge.b} has ${faces.size} incident faces $faces; " +
                "a=${boundary.positions[edge.a]}, b=${boundary.positions[edge.b]}, " +
                "polygons=${faces.map { boundary.faceVertexIds[it] }}",
        )
    }
    if (boundaryEdges.size > maximumEdges) {
        throw TransformApplicabilityException(
            CoreIssueCode.TooLarge,
            "Resolved boundary exceeds the $maximumEdges-edge limit",
        )
    }
    progress?.reportProgress(90)
    return boundary
}

private data class SourceTriangle(
    val sourceFaceId: Int,
    val a: Vec3,
    val b: Vec3,
    val c: Vec3,
    /** Absolute source-face winding represented by this one presentation triangle. */
    val windingMultiplicity: Int = 1,
    /** Independently closed STL presentation piece, or negative for one global winding soup. */
    val solidId: Int = -1,
) {
    val ab: Vec3 = b - a
    val ac: Vec3 = c - a
    val edgeScale: Double = maxOf(ab.norm, ac.norm, (c - b).norm)
    val normal: Vec3 = (ab cross ac).unit
    val planeDistance: Double = normal * a
    val minX = minOf(a.x, b.x, c.x)
    val maxX = maxOf(a.x, b.x, c.x)
    val minY = minOf(a.y, b.y, c.y)
    val maxY = maxOf(a.y, b.y, c.y)
    val minZ = minOf(a.z, b.z, c.z)
    val maxZ = maxOf(a.z, b.z, c.z)
    val edges: List<Segment3>
        get() = listOf(Segment3(a, b), Segment3(b, c), Segment3(c, a))

    fun boundsOverlap(other: SourceTriangle, tolerance: Double): Boolean =
        minY <= other.maxY + tolerance && other.minY <= maxY + tolerance &&
            minZ <= other.maxZ + tolerance && other.minZ <= maxZ + tolerance
}

private fun Polyhedron.resolvedSurfaceTriangles(): List<SourceTriangle> = fs.flatMap { face ->
    val resolved = resolvedFaces[face.id]
    resolved.cells.flatMap { cell ->
        cell.triangles.map { triangle ->
            SourceTriangle(
                face.id,
                resolved.vertices[triangle.a].position,
                resolved.vertices[triangle.b].position,
                resolved.vertices[triangle.c].position,
                windingMultiplicity = abs(cell.winding),
                solidId = if (isCompound) vertexComponentIds[face.fvs.first().id] else -1,
            )
        }
    }
}

private data class Segment3(val a: Vec3, val b: Vec3)

private fun triangleIntersectionSegment(
    first: SourceTriangle,
    second: SourceTriangle,
    tolerance: Double,
): Segment3? {
    val line = first.normal cross second.normal
    if (line.norm <= EPS) {
        // Coplanar presentation triangles from separate source faces are either an allowed shared
        // boundary contact or a forbidden positive-area overlap. They do not create a transverse
        // cut; the embedded-boundary validator rejects any remaining overlap in the result.
        return null
    }
    val direction = line.unit
    val firstPoints = first.intersectionsWithPlane(second.normal, second.planeDistance, tolerance)
    val secondPoints = second.intersectionsWithPlane(first.normal, first.planeDistance, tolerance)
    if (firstPoints.size < 2 || secondPoints.size < 2) return null
    val origin = firstPoints.first()
    val firstRange = firstPoints.minMaxAlong(origin, direction)
    val secondRange = secondPoints.minMaxAlong(origin, direction)
    val start = maxOf(firstRange.first, secondRange.first)
    val end = minOf(firstRange.second, secondRange.second)
    if (end - start <= tolerance) return null
    return Segment3(origin + direction * start, origin + direction * end)
}

/** Positive-area coplanar overlap is an invalid input, not a transverse arrangement cut. */
private fun SourceTriangle.hasPositiveAreaCoplanarOverlap(
    other: SourceTriangle,
    tolerance: Double,
): Boolean {
    if ((normal cross other.normal).norm > EPS * 32.0 || abs(normal * other.a - planeDistance) > tolerance) {
        return false
    }
    val axis = when {
        abs(normal.x) >= abs(normal.y) && abs(normal.x) >= abs(normal.z) -> 0
        abs(normal.y) >= abs(normal.z) -> 1
        else -> 2
    }
    fun Vec3.project() = when (axis) {
        0 -> Point2(y, z)
        1 -> Point2(x, z)
        else -> Point2(x, y)
    }
    var polygon = listOf(a.project(), b.project(), c.project())
    val clip = listOf(other.a.project(), other.b.project(), other.c.project())
    val orientation = signedArea(clip).let { if (it >= 0.0) 1.0 else -1.0 }
    for (index in clip.indices) {
        val edgeA = clip[index]
        val edgeB = clip[(index + 1) % clip.size]
        if (polygon.isEmpty()) return false
        val output = ArrayList<Point2>()
        for (pointIndex in polygon.indices) {
            val current = polygon[pointIndex]
            val previous = polygon[(pointIndex + polygon.size - 1) % polygon.size]
            val currentDistance = orientation * ((edgeB - edgeA) cross2 (current - edgeA))
            val previousDistance = orientation * ((edgeB - edgeA) cross2 (previous - edgeA))
            val currentInside = currentDistance >= -tolerance
            val previousInside = previousDistance >= -tolerance
            if (currentInside != previousInside) {
                val denominator = previousDistance - currentDistance
                if (abs(denominator) > tolerance * tolerance) {
                    val fraction = previousDistance / denominator
                    output += previous + (current - previous) * fraction
                }
            }
            if (currentInside) output += current
        }
        polygon = output
    }
    return abs(signedArea(polygon)) > tolerance * tolerance
}

private data class Point2(val x: Double, val y: Double) {
    operator fun plus(other: Point2) = Point2(x + other.x, y + other.y)
    operator fun minus(other: Point2) = Point2(x - other.x, y - other.y)
    operator fun times(value: Double) = Point2(x * value, y * value)
}

private infix fun Point2.cross2(other: Point2): Double = x * other.y - y * other.x

private fun signedArea(points: List<Point2>): Double = points.indices.sumOf { index ->
    points[index] cross2 points[(index + 1) % points.size]
} / 2.0

private fun SourceTriangle.intersectionsWithPlane(
    planeNormal: Vec3,
    planeDistance: Double,
    tolerance: Double,
): List<Vec3> {
    val vertices = listOf(a, b, c)
    val result = ArrayList<Vec3>(3)
    for (index in vertices.indices) {
        val start = vertices[index]
        val end = vertices[(index + 1) % vertices.size]
        val startDistance = planeNormal * start - planeDistance
        val endDistance = planeNormal * end - planeDistance
        if (abs(startDistance) <= tolerance) result.addDistinct(start, tolerance)
        if (startDistance * endDistance < -tolerance * tolerance) {
            val fraction = startDistance / (startDistance - endDistance)
            result.addDistinct(start + (end - start) * fraction, tolerance)
        }
    }
    return result
}

private fun MutableList<Vec3>.addDistinct(point: Vec3, tolerance: Double) {
    if (none { existing -> (existing - point).norm <= tolerance }) add(point)
}

private fun List<Vec3>.minMaxAlong(origin: Vec3, direction: Vec3): Pair<Double, Double> {
    val values = map { point -> (point - origin) * direction }
    return values.minOrNull()!! to values.maxOrNull()!!
}

private data class CutLine(val normal: Vec3, val distance: Double)

private fun MutableList<CutLine>.addCut(
    triangle: SourceTriangle,
    segment: Segment3,
    tolerance: Double,
) {
    val direction = segment.b - segment.a
    var normal = (direction cross triangle.normal).unit
    var distance = normal * segment.a
    if (distance < -tolerance || abs(distance) <= tolerance &&
        listOf(normal.x, normal.y, normal.z).firstOrNull { abs(it) > EPS }?.let { it < 0.0 } == true
    ) {
        normal = normal * -1.0
        distance = -distance
    }
    if (none { existing ->
            (existing.normal - normal).norm <= EPS * 32.0 && abs(existing.distance - distance) <= tolerance
        }
    ) add(CutLine(normal, distance))
}

private fun SourceTriangle.splitBy(cuts: List<CutLine>, tolerance: Double): List<List<Vec3>> {
    var polygons = listOf(listOf(a, b, c))
    for (cut in cuts.sortedWith(compareBy(CutLine::distance, { it.normal.x }, { it.normal.y }, { it.normal.z }))) {
        polygons = polygons.flatMap { polygon -> polygon.splitBy(cut, tolerance) }
    }
    return polygons
}

private fun List<Vec3>.splitBy(cut: CutLine, tolerance: Double): List<List<Vec3>> {
    val distances = map { point -> cut.normal * point - cut.distance }
    if (distances.none { it > tolerance } || distances.none { it < -tolerance }) return listOf(this)

    fun clipped(positive: Boolean): List<Vec3> {
        val result = ArrayList<Vec3>()
        for (index in indices) {
            val a = this[index]
            val b = this[(index + 1) % size]
            val da = distances[index]
            val db = distances[(index + 1) % size]
            val aInside = if (positive) da >= -tolerance else da <= tolerance
            val bInside = if (positive) db >= -tolerance else db <= tolerance
            if (aInside) result += a
            if (aInside != bInside && abs(da - db) > tolerance) {
                result += a + (b - a) * (da / (da - db))
            }
        }
        return result.cleanPolygon(tolerance)
    }

    return listOf(clipped(true), clipped(false)).filter { it.size >= 3 }
}

private fun List<Vec3>.cleanPolygon(tolerance: Double): List<Vec3> {
    val result = ArrayList<Vec3>()
    for (point in this) {
        if (result.isEmpty() || (result.last() - point).norm > tolerance) result += point
    }
    if (result.size > 1 && (result.first() - result.last()).norm <= tolerance) result.removeLast()
    var changed = true
    while (changed && result.size > 3) {
        changed = false
        for (index in result.indices) {
            val previous = result[(index + result.size - 1) % result.size]
            val current = result[index]
            val next = result[(index + 1) % result.size]
            if (((current - previous) cross (next - current)).norm <= tolerance *
                maxOf((current - previous).norm, (next - current).norm)
            ) {
                result.removeAt(index)
                changed = true
                break
            }
        }
    }
    return result
}

private data class OrientedTriangle(
    val a: Vec3,
    val b: Vec3,
    val c: Vec3,
    val sourceFaceId: Int,
) {
    val area2: Double get() = ((b - a) cross (c - a)).norm
    val normal: Vec3 get() = ((b - a) cross (c - a)).unit
    val center: Vec3 get() = (a + b + c) * (1.0 / 3.0)
    val shortestEdge: Double get() = minOf((b - a).norm, (c - b).norm, (a - c).norm)
    fun reversed() = OrientedTriangle(a, c, b, sourceFaceId)
}

/** Splits both sides of every T-junction before vertex indexing and manifold validation. */
private fun List<OrientedTriangle>.conformEdges(tolerance: Double): List<OrientedTriangle> {
    val points = ArrayList<Vec3>()
    val buckets = HashMap<Bucket, MutableList<Int>>()
    fun bucket(point: Vec3) = Bucket(
        floor(point.x / tolerance).toLong(),
        floor(point.y / tolerance).toLong(),
        floor(point.z / tolerance).toLong(),
    )
    fun pointIndex(point: Vec3): Int {
        val key = bucket(point)
        for (dx in -1L..1L) for (dy in -1L..1L) for (dz in -1L..1L) {
            for (candidate in buckets[Bucket(key.x + dx, key.y + dy, key.z + dz)].orEmpty()) {
                if ((points[candidate] - point).norm <= tolerance) return candidate
            }
        }
        val index = points.size
        points += point
        buckets.getOrPut(key, ::arrayListOf) += index
        return index
    }
    val trianglePointIds = map { triangle ->
        listOf(pointIndex(triangle.a), pointIndex(triangle.b), pointIndex(triangle.c))
    }
    val sortedPointIds = List(3) { axis ->
        points.indices.sortedBy { index -> points[index].component(axis) }
    }
    fun List<Int>.lowerBound(value: Double, axis: Int): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (points[this[middle]].component(axis) < value) low = middle + 1 else high = middle
        }
        return low
    }
    val edgeSplits = HashMap<IndexEdge, List<Int>>()
    fun splitPoints(first: Int, second: Int): List<Int> {
        val key = indexEdge(first, second)
        val canonical = edgeSplits.getOrPut(key) {
            val a = points[key.a]
            val b = points[key.b]
            val edge = b - a
            val length = edge.norm
            val lengthSquared = edge * edge
            val axis = edge.dominantAxis()
            val minimum = minOf(a.component(axis), b.component(axis)) - tolerance
            val maximum = maxOf(a.component(axis), b.component(axis)) + tolerance
            val candidates = sortedPointIds[axis]
            val start = candidates.lowerBound(minimum, axis)
            buildList {
                for (position in start until candidates.size) {
                    val pointId = candidates[position]
                    val point = points[pointId]
                    if (point.component(axis) > maximum) break
                    if (point.x !in minOf(a.x, b.x) - tolerance..maxOf(a.x, b.x) + tolerance ||
                        point.y !in minOf(a.y, b.y) - tolerance..maxOf(a.y, b.y) + tolerance ||
                        point.z !in minOf(a.z, b.z) - tolerance..maxOf(a.z, b.z) + tolerance
                    ) continue
                    val fraction = ((point - a) * edge) / lengthSquared
                    if (fraction <= tolerance / length || fraction >= 1.0 - tolerance / length) continue
                    if ((a + edge * fraction - point).norm <= tolerance) add(fraction to pointId)
                }
            }.sortedBy(Pair<Double, Int>::first).map(Pair<Double, Int>::second)
        }
        return if (first == key.a) canonical else canonical.asReversed()
    }

    val result = ArrayList<OrientedTriangle>()
    for ((triangleIndex, triangle) in withIndex()) {
        val corners = trianglePointIds[triangleIndex]
        val boundary = ArrayList<Vec3>()
        for (index in corners.indices) {
            val a = corners[index]
            val b = corners[(index + 1) % corners.size]
            boundary += points[a]
            splitPoints(a, b).forEach { pointIndex ->
                val point = points[pointIndex]
                if ((boundary.last() - point).norm > tolerance) boundary += point
            }
        }
        if (boundary.size == 3) {
            result += triangle
            continue
        }
        var center: Vec3 = Vec3.ZERO
        for (point in boundary) center += point
        center *= 1.0 / boundary.size
        for (index in boundary.indices) {
            val fragment = OrientedTriangle(
                center,
                boundary[index],
                boundary[(index + 1) % boundary.size],
                triangle.sourceFaceId,
            )
            if (fragment.area2 > tolerance * tolerance) result += fragment
        }
    }
    return result
}

private fun Vec3.component(axis: Int): Double = when (axis) {
    0 -> x
    1 -> y
    else -> z
}

private fun Vec3.dominantAxis(): Int = when {
    abs(x) >= abs(y) && abs(x) >= abs(z) -> 0
    abs(y) >= abs(z) -> 1
    else -> 2
}

private class WindingClassifier(
    source: List<SourceTriangle>,
    private val tolerance: Double,
) {
    private val independentSolids = source.all { triangle -> triangle.solidId >= 0 }
    private val groups: List<WindingGroup> = if (independentSolids) {
        source.groupBy(SourceTriangle::solidId).entries.sortedBy { entry -> entry.key }.map { (solidId, triangles) ->
            WindingGroup(solidId, triangles)
        }
    } else {
        listOf(WindingGroup(-1, source))
    }
    private val index = WindingGroupIndex(groups)

    fun hasNonzeroWinding(point: Vec3): Boolean = index.contains(point, tolerance)

}

/** A small AABB tree keeps Boolean unions of many presentation pieces cheap to classify. */
private class WindingGroupIndex(groups: List<WindingGroup>) {
    private val minX = groups.minOf(WindingGroup::minX)
    private val maxX = groups.maxOf(WindingGroup::maxX)
    private val minY = groups.minOf(WindingGroup::minY)
    private val maxY = groups.maxOf(WindingGroup::maxY)
    private val minZ = groups.minOf(WindingGroup::minZ)
    private val maxZ = groups.maxOf(WindingGroup::maxZ)
    private val leaf = groups.takeIf { it.size <= 8 }
    private val children: List<WindingGroupIndex> = if (leaf != null) {
        emptyList()
    } else {
        val spans = listOf(maxX - minX, maxY - minY, maxZ - minZ)
        val axis = spans.indices.maxBy(spans::get)
        val ordered = groups.sortedBy { group -> group.center(axis) }
        val middle = ordered.size / 2
        listOf(WindingGroupIndex(ordered.subList(0, middle)), WindingGroupIndex(ordered.subList(middle, ordered.size)))
    }

    fun contains(point: Vec3, tolerance: Double): Boolean {
        if (point.x !in minX - tolerance..maxX + tolerance ||
            point.y !in minY - tolerance..maxY + tolerance ||
            point.z !in minZ - tolerance..maxZ + tolerance
        ) return false
        return leaf?.any { group -> group.contains(point, tolerance) }
            ?: children.any { child -> child.contains(point, tolerance) }
    }
}

private class WindingGroup(
    val solidId: Int,
    val triangles: List<SourceTriangle>,
) {
    val minX = triangles.minOf(SourceTriangle::minX)
    val maxX = triangles.maxOf(SourceTriangle::maxX)
    val minY = triangles.minOf { triangle -> minOf(triangle.a.y, triangle.b.y, triangle.c.y) }
    val maxY = triangles.maxOf { triangle -> maxOf(triangle.a.y, triangle.b.y, triangle.c.y) }
    val minZ = triangles.minOf { triangle -> minOf(triangle.a.z, triangle.b.z, triangle.c.z) }
    val maxZ = triangles.maxOf { triangle -> maxOf(triangle.a.z, triangle.b.z, triangle.c.z) }
    private val triangleIndex = WindingTriangleIndex(triangles)

    fun center(axis: Int): Double = when (axis) {
        0 -> (minX + maxX) / 2.0
        1 -> (minY + maxY) / 2.0
        else -> (minZ + maxZ) / 2.0
    }

    fun boundsContain(point: Vec3, tolerance: Double): Boolean =
        point.x in minX - tolerance..maxX + tolerance &&
            point.y in minY - tolerance..maxY + tolerance &&
            point.z in minZ - tolerance..maxZ + tolerance

    fun contains(point: Vec3, tolerance: Double): Boolean {
        if (!boundsContain(point, tolerance)) return false
        // Axis rays compute the same generalized winding for embedded and immersed closed
        // surfaces. Retry degeneracies from another direction, then retain solid angle as the
        // boundary-safe fallback when every ray touches an edge, vertex, or coplanar triangle.
        for (axis in 0..2) {
            triangleIndex.rayWindingOrNull(point, axis, tolerance)?.let { winding -> return winding != 0 }
        }
        return triangles.hasNonzeroSolidAngleWinding(point, tolerance)
    }
}

/**
 * A ray only needs triangles whose two-dimensional projection contains the query point and whose
 * positive-axis extent reaches it. Resolved classifies many fragments against the same triangle
 * soup, so indexing those three ray queries avoids rescanning the complete surface for each side
 * of every fragment.
 */
private class WindingTriangleIndex private constructor(
    triangles: List<SourceTriangle>,
    private val minX: Double,
    private val maxX: Double,
    private val minY: Double,
    private val maxY: Double,
    private val minZ: Double,
    private val maxZ: Double,
) {
    constructor(triangles: List<SourceTriangle>) : this(
        triangles,
        triangles.minOf(SourceTriangle::minX),
        triangles.maxOf(SourceTriangle::maxX),
        triangles.minOf(SourceTriangle::minY),
        triangles.maxOf(SourceTriangle::maxY),
        triangles.minOf(SourceTriangle::minZ),
        triangles.maxOf(SourceTriangle::maxZ),
    )

    private val leaf = triangles.takeIf { it.size <= 8 }
    private val children: List<WindingTriangleIndex> = if (leaf != null) {
        emptyList()
    } else {
        val spans = listOf(maxX - minX, maxY - minY, maxZ - minZ)
        val splitAxis = spans.indices.maxBy(spans::get)
        val ordered = triangles.sortedBy { triangle ->
            when (splitAxis) {
                0 -> (triangle.minX + triangle.maxX) / 2.0
                1 -> (triangle.minY + triangle.maxY) / 2.0
                else -> (triangle.minZ + triangle.maxZ) / 2.0
            }
        }
        val middle = ordered.size / 2
        listOf(
            WindingTriangleIndex(ordered.subList(0, middle)),
            WindingTriangleIndex(ordered.subList(middle, ordered.size)),
        )
    }

    fun rayWindingOrNull(point: Vec3, axis: Int, tolerance: Double): Int? {
        val reachesPositiveRay = when (axis) {
            0 -> point.x <= maxX + tolerance &&
                point.y in minY - tolerance..maxY + tolerance &&
                point.z in minZ - tolerance..maxZ + tolerance
            1 -> point.y <= maxY + tolerance &&
                point.x in minX - tolerance..maxX + tolerance &&
                point.z in minZ - tolerance..maxZ + tolerance
            else -> point.z <= maxZ + tolerance &&
                point.x in minX - tolerance..maxX + tolerance &&
                point.y in minY - tolerance..maxY + tolerance
        }
        if (!reachesPositiveRay) return 0
        leaf?.let { triangles ->
            var winding = 0
            for (triangle in triangles) {
                val contribution = triangle.rayWindingContributionOrNull(point, axis, tolerance)
                    ?: return null
                winding += contribution
            }
            return winding
        }
        var winding = 0
        for (child in children) {
            winding += child.rayWindingOrNull(point, axis, tolerance) ?: return null
        }
        return winding
    }
}

private fun SourceTriangle.rayWindingContributionOrNull(
    point: Vec3,
    axis: Int,
    tolerance: Double,
): Int? {
    val direction = when (axis) {
        0 -> Vec3(1.0, 0.0, 0.0)
        1 -> Vec3(0.0, 1.0, 0.0)
        else -> Vec3(0.0, 0.0, 1.0)
    }
    val projectionContainsPoint = when (axis) {
        0 -> point.y in minY - tolerance..maxY + tolerance &&
            point.z in minZ - tolerance..maxZ + tolerance
        1 -> point.x in minX - tolerance..maxX + tolerance &&
            point.z in minZ - tolerance..maxZ + tolerance
        else -> point.x in minX - tolerance..maxX + tolerance &&
            point.y in minY - tolerance..maxY + tolerance
    }
    if (!projectionContainsPoint) return 0
    val p = direction cross ac
    val determinant = ab * p
    if (abs(determinant) <= tolerance * edgeScale) return 0
    val inverse = 1.0 / determinant
    val offset = point - a
    val u = (offset * p) * inverse
    val q = offset cross ab
    val v = (direction * q) * inverse
    val barycentricTolerance = tolerance / edgeScale
    if (u < -barycentricTolerance || v < -barycentricTolerance ||
        u + v > 1.0 + barycentricTolerance
    ) return 0
    val distance = (ac * q) * inverse
    if (distance < -tolerance) return 0
    if (distance <= tolerance || u <= barycentricTolerance || v <= barycentricTolerance ||
        1.0 - u - v <= barycentricTolerance
    ) return null
    return windingMultiplicity * if (normal * direction > 0.0) 1 else -1
}

private fun List<SourceTriangle>.hasNonzeroSolidAngleWinding(point: Vec3, tolerance: Double): Boolean {
    var solidAngle = 0.0
    for (triangle in this) {
        val a = triangle.a - point
        val b = triangle.b - point
        val c = triangle.c - point
        val la = a.norm
        val lb = b.norm
        val lc = c.norm
        if (minOf(la, lb, lc) <= tolerance) return true
        val numerator = a * (b cross c)
        val denominator = la * lb * lc + (a * b) * lc + (b * c) * la + (c * a) * lb
        solidAngle += triangle.windingMultiplicity * 2.0 * atan2(numerator, denominator)
    }
    return abs(solidAngle / (4.0 * PI)) > 0.5
}

private data class TriangleBoundary(
    val positions: List<Vec3>,
    val triangles: List<IndexedTriangle>,
)

private data class IndexedTriangle(
    val vertexIds: List<Int>,
    val sourceFaceIds: Set<Int>,
)

private data class PolygonBoundary(
    val positions: List<Vec3>,
    val faceVertexIds: List<List<Int>>,
    val faceSourceIds: List<Set<Int>>,
)

private fun TriangleBoundary.asTrianglePolygons(): PolygonBoundary = PolygonBoundary(
    positions = positions,
    faceVertexIds = triangles.map(IndexedTriangle::vertexIds),
    faceSourceIds = triangles.map(IndexedTriangle::sourceFaceIds),
)

private fun PolygonBoundary.edgeKeys(): Set<IndexEdge> = buildSet {
    for (face in faceVertexIds) for (index in face.indices) {
        add(indexEdge(face[index], face[(index + 1) % face.size]))
    }
}

private data class PolygonRecord(
    val vertexIds: List<Int>,
    val sourceFaceIds: Set<Int>,
)

private data class Bucket(val x: Long, val y: Long, val z: Long)

private fun buildTriangleBoundary(
    fragments: List<OrientedTriangle>,
    tolerance: Double,
): TriangleBoundary {
    val positions = ArrayList<Vec3>()
    val buckets = HashMap<Bucket, MutableList<Int>>()
    fun bucket(point: Vec3) = Bucket(
        floor(point.x / tolerance).toLong(),
        floor(point.y / tolerance).toLong(),
        floor(point.z / tolerance).toLong(),
    )
    fun vertexIndex(point: Vec3): Int {
        val key = bucket(point)
        for (dx in -1L..1L) for (dy in -1L..1L) for (dz in -1L..1L) {
            for (candidate in buckets[Bucket(key.x + dx, key.y + dy, key.z + dz)].orEmpty()) {
                if ((positions[candidate] - point).norm <= tolerance) return candidate
            }
        }
        val index = positions.size
        positions += point
        buckets.getOrPut(key, ::arrayListOf) += index
        return index
    }

    val triangles = fragments.mapNotNull { fragment ->
        val ids = listOf(vertexIndex(fragment.a), vertexIndex(fragment.b), vertexIndex(fragment.c))
        ids.takeIf { it.toSet().size == 3 }?.let { IndexedTriangle(it, setOf(fragment.sourceFaceId)) }
    }
    val grouped = triangles.groupBy { triangle -> triangle.vertexIds.sorted() }
    val unique = grouped.values.mapNotNull { coincident ->
        // Repeated fragments with the same orientation are numerical duplicates. Opposite copies
        // cancel as an internal interface and must not survive as a non-manifold double wall.
        val first = coincident.first()
        val same = coincident.count { candidate ->
            candidate.vertexIds.isSameOrientationAs(first.vertexIds)
        }
        val opposite = coincident.size - same
        val sources = coincident.flatMapTo(linkedSetOf()) { it.sourceFaceIds }
            .sorted().toCollection(linkedSetOf())
        when {
            same > opposite -> IndexedTriangle(first.vertexIds, sources)
            opposite > same -> IndexedTriangle(
                listOf(first.vertexIds[0], first.vertexIds[2], first.vertexIds[1]),
                sources,
            )
            else -> null
        }
    }.sortedWith(compareBy(
        { it.vertexIds.minOrNull() },
        { it.vertexIds.sorted()[1] },
        { it.vertexIds.maxOrNull() },
    ))
    return TriangleBoundary(positions, unique)
}

private data class IndexEdge(val a: Int, val b: Int)
private fun indexEdge(a: Int, b: Int) = if (a < b) IndexEdge(a, b) else IndexEdge(b, a)

/** Maximal safe merge of coplanar triangles from one logical source region. */
private fun TriangleBoundary.mergeCoplanarFaces(tolerance: Double): PolygonBoundary {
    val parent = IntArray(triangles.size) { it }
    fun find(index: Int): Int {
        var root = index
        while (parent[root] != root) root = parent[root]
        var current = index
        while (parent[current] != current) {
            val next = parent[current]
            parent[current] = root
            current = next
        }
        return root
    }
    fun union(first: Int, second: Int) {
        val a = find(first)
        val b = find(second)
        if (a != b) parent[maxOf(a, b)] = minOf(a, b)
    }

    val edgeUses = HashMap<IndexEdge, MutableList<Int>>()
    for ((index, triangle) in triangles.withIndex()) {
        val ids = triangle.vertexIds
        for (edgeIndex in ids.indices) {
            edgeUses.getOrPut(indexEdge(ids[edgeIndex], ids[(edgeIndex + 1) % ids.size]), ::arrayListOf) += index
        }
    }
    for (uses in edgeUses.values) {
        for (firstIndex in uses.indices) for (secondIndex in (firstIndex + 1) until uses.size) {
            val first = triangles[uses[firstIndex]]
            val second = triangles[uses[secondIndex]]
            if (first.sourceFaceIds == second.sourceFaceIds && first.isCoplanarWith(second, positions, tolerance)) {
                union(uses[firstIndex], uses[secondIndex])
            }
        }
    }

    val components = triangles.indices.groupBy(::find).values
    val faces = ArrayList<PolygonRecord>()
    for (component in components) {
        val sourceFaceIds = component.flatMapTo(linkedSetOf()) { triangleIndex ->
            triangles[triangleIndex].sourceFaceIds
        }.sorted().toCollection(linkedSetOf())
        val directedUses = HashMap<IndexEdge, MutableList<Pair<Int, Int>>>()
        for (triangleIndex in component) {
            val ids = triangles[triangleIndex].vertexIds
            for (edgeIndex in ids.indices) {
                val a = ids[edgeIndex]
                val b = ids[(edgeIndex + 1) % ids.size]
                directedUses.getOrPut(indexEdge(a, b), ::arrayListOf) += a to b
            }
        }
        val boundaryEdges = directedUses.values.filter { it.size == 1 }.map { it.single() }
        val merged = boundaryEdges.singleCycleOrNull()
        if (merged != null && merged.size >= 3) {
            faces += PolygonRecord(merged, sourceFaceIds)
        } else {
            component.forEach { triangleIndex ->
                faces += PolygonRecord(triangles[triangleIndex].vertexIds, sourceFaceIds)
            }
        }
    }

    val orderedFaces = simplifyDegreeTwoVertices(faces, positions, tolerance).sortedWith(compareBy<PolygonRecord>(
        { it.vertexIds.minOrNull() },
        { it.vertexIds.size },
        { it.vertexIds.joinToString(",") },
    ))
    val outputEdgeUses = orderedFaces.flatMapIndexed { faceIndex, face ->
        face.vertexIds.indices.map { index ->
            indexEdge(
                face.vertexIds[index],
                face.vertexIds[(index + 1) % face.vertexIds.size],
            ) to faceIndex
        }
    }.groupBy({ it.first }, { it.second })
    outputEdgeUses.entries.firstOrNull { it.value.size != 2 }?.let { edge ->
        throw TransformApplicabilityException(
            CoreIssueCode.TransformNotApplicable,
            "Merged Resolved edge ${edge.key} has ${edge.value.size} incident faces ${edge.value}; " +
                "positions=${positions[edge.key.a]},${positions[edge.key.b]}; details=${edge.value.map { faceIndex ->
                    val face = orderedFaces[faceIndex]
                    val a = positions[face.vertexIds[0]]
                    val normal = ((positions[face.vertexIds[1]] - a) cross
                        (positions[face.vertexIds[2]] - a)).unit
                    face.vertexIds to (normal to face.sourceFaceIds)
                }}",
        )
    }
    val used = orderedFaces.flatMap(PolygonRecord::vertexIds).distinct().sorted()
    val reindex = used.withIndex().associate { (newIndex, oldIndex) -> oldIndex to newIndex }
    return PolygonBoundary(
        used.map(positions::get),
        orderedFaces.map { face -> face.vertexIds.map(reindex::getValue) },
        orderedFaces.map(PolygonRecord::sourceFaceIds),
    )
}

/** Removes arrangement-only points lying in the middle of one physical two-face edge. */
private fun simplifyDegreeTwoVertices(
    inputFaces: List<PolygonRecord>,
    positions: List<Vec3>,
    tolerance: Double,
): List<PolygonRecord> {
    val faces = inputFaces.map { face -> face.vertexIds.toMutableList() }
    val incidentFaces = List(positions.size) { linkedSetOf<Int>() }
    for ((faceIndex, face) in faces.withIndex()) {
        for (vertex in face) incidentFaces[vertex] += faceIndex
    }
    val pending = ArrayDeque<Int>()
    val queued = BooleanArray(positions.size)
    fun enqueue(vertex: Int) {
        if (!queued[vertex] && incidentFaces[vertex].isNotEmpty()) {
            queued[vertex] = true
            pending += vertex
        }
    }
    positions.indices.forEach(::enqueue)
    while (pending.isNotEmpty()) {
        val candidate = pending.removeFirst()
        queued[candidate] = false
        val incident = incidentFaces[candidate]
        if (incident.isEmpty()) continue
        val occurrences = incident.map { faceIndex ->
            val face = faces[faceIndex]
            faceIndex to face.indexOf(candidate)
        }
        if (occurrences.any { (faceIndex, index) ->
                val face = faces[faceIndex]
                if (face.size <= 3 || index < 0) return@any true
                val previous = positions[face[(index + face.size - 1) % face.size]]
                val current = positions[candidate]
                val next = positions[face[(index + 1) % face.size]]
                val a = previous - current
                val b = next - current
                (a cross b).norm > tolerance * maxOf(a.norm, b.norm) || a * b >= 0.0
            }
        ) continue
        for ((faceIndex, index) in occurrences) {
            val face = faces[faceIndex]
            val previous = face[(index + face.size - 1) % face.size]
            val next = face[(index + 1) % face.size]
            face.removeAt(index)
            enqueue(previous)
            enqueue(next)
        }
        incident.clear()
    }
    return faces.mapIndexed { index, vertexIds ->
        PolygonRecord(vertexIds, inputFaces[index].sourceFaceIds)
    }
}

private fun PolygonBoundary.sourceProvenance(
    source: Polyhedron,
    tolerance: Double,
): ResolvedTopologyProvenance {
    val incidentSourceFaces = List(positions.size) { linkedSetOf<Int>() }
    for ((faceIndex, face) in faceVertexIds.withIndex()) {
        for (vertex in face) incidentSourceFaces[vertex] += faceSourceIds[faceIndex]
    }

    fun sourceEdgesAt(point: Vec3): List<Int> = source.es.indices.filter { edgeIndex ->
        point.parameterOnSegmentOrNull(source.es[edgeIndex].a, source.es[edgeIndex].b, tolerance) != null
    }

    val vertexProvenance = positions.mapIndexed { vertexIndex, point ->
        val sourceVertices = source.vs.filter { vertex -> (vertex - point).norm <= tolerance }.map { it.id }
        val segmentPoints = source.fs.flatMap { face ->
            face.fvs.indices.mapNotNull { segmentIndex ->
                point.parameterOnSegmentOrNull(
                    face.fvs[segmentIndex],
                    face.fvs[(segmentIndex + 1) % face.fvs.size],
                    tolerance,
                )?.let { parameter -> SourceSegmentPoint(face.id, segmentIndex, parameter) }
            }
        }.sortedWith(compareBy(SourceSegmentPoint::sourceFaceId, SourceSegmentPoint::sourceSegmentIndex))
        ResolvedElementProvenance(
            sourceVertexIds = sourceVertices.sorted(),
            sourceEdgeIds = sourceEdgesAt(point),
            sourceFaceIds = incidentSourceFaces[vertexIndex].toList(),
            sourceSegmentPoints = segmentPoints,
        )
    }

    val edgeSources = HashMap<IndexEdge, MutableSet<Int>>()
    for ((faceIndex, face) in faceVertexIds.withIndex()) {
        for (index in face.indices) {
            edgeSources.getOrPut(indexEdge(face[index], face[(index + 1) % face.size]), ::linkedSetOf) +=
                faceSourceIds[faceIndex]
        }
    }
    val clockwiseFaces = faceVertexIds.map(List<Int>::asReversed)
    val edgeOrder = buildList {
        val added = HashSet<IndexEdge>()
        for (face in clockwiseFaces) for (index in face.indices) {
            val a = face[index]
            val b = face[(index + 1) % face.size]
            if (a < b && added.add(indexEdge(a, b))) add(indexEdge(a, b))
        }
    }
    val edgeProvenance = edgeOrder.map { edge ->
        val commonSourceEdges = sourceEdgesAt(positions[edge.a]).intersect(sourceEdgesAt(positions[edge.b]).toSet())
        ResolvedElementProvenance(
            sourceEdgeIds = commonSourceEdges.sorted(),
            sourceFaceIds = edgeSources[edge].orEmpty().toList().sorted(),
        )
    }
    val faceProvenance = faceSourceIds.map { sources ->
        ResolvedElementProvenance(
            sourceFaceIds = sources.toList().sorted(),
            sourceCellIds = sources.flatMap { faceId ->
                source.resolvedFaces[faceId].cells.map { cell -> cell.id }
            }.distinct().sorted(),
        )
    }
    return ResolvedTopologyProvenance(vertexProvenance, edgeProvenance, faceProvenance)
}

private fun Vec3.parameterOnSegmentOrNull(a: Vec3, b: Vec3, tolerance: Double): Double? {
    val edge = b - a
    val lengthSquared = edge * edge
    if (lengthSquared <= tolerance * tolerance) return null
    val parameter = ((this - a) * edge) / lengthSquared
    if (parameter !in -tolerance / edge.norm..(1.0 + tolerance / edge.norm)) return null
    val bounded = parameter.coerceIn(0.0, 1.0)
    return bounded.takeIf { ((a + edge * bounded) - this).norm <= tolerance }
}

private fun IndexedTriangle.isCoplanarWith(
    other: IndexedTriangle,
    positions: List<Vec3>,
    tolerance: Double,
): Boolean {
    val a = positions[vertexIds[0]]
    val normal = ((positions[vertexIds[1]] - a) cross (positions[vertexIds[2]] - a)).unit
    val otherA = positions[other.vertexIds[0]]
    val otherNormal = (
        (positions[other.vertexIds[1]] - otherA) cross (positions[other.vertexIds[2]] - otherA)
        ).unit
    return (normal cross otherNormal).norm <= EPS * 32.0 &&
        abs(normal * otherA - normal * a) <= tolerance
}

private fun List<Pair<Int, Int>>.singleCycleOrNull(): List<Int>? {
    if (isEmpty()) return null
    val outgoing = groupBy({ it.first }, { it.second })
    val incoming = groupBy({ it.second }, { it.first })
    if (outgoing.values.any { it.size != 1 } || incoming.values.any { it.size != 1 } ||
        outgoing.keys != incoming.keys
    ) return null
    val start = outgoing.keys.minOrNull() ?: return null
    val result = ArrayList<Int>()
    var current = start
    do {
        if (current in result) return null
        result += current
        current = outgoing.getValue(current).single()
    } while (current != start && result.size <= size)
    return result.takeIf { it.size == size }
}

private fun List<Int>.isSameOrientationAs(other: List<Int>): Boolean =
    indices.any { offset -> indices.all { index -> this[(index + offset) % size] == other[index] } }
