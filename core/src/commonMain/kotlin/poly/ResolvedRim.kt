package polyhedra.core.poly

import polyhedra.model.poly.Face
import polyhedra.model.poly.FaceRim
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.ResolvedFaceGeometry
import polyhedra.model.poly.ResolvedRimCycle
import polyhedra.model.poly.ResolvedRimGeometry
import polyhedra.model.poly.ResolvedRimRegion
import polyhedra.model.poly.SourceEdgeOccurrence
import polyhedra.model.util.EPS
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.plus
import polyhedra.model.util.times
import polyhedra.model.util.unit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

private data class RimPoint(val x: Double, val y: Double) {
    operator fun plus(other: RimPoint) = RimPoint(x + other.x, y + other.y)
    operator fun minus(other: RimPoint) = RimPoint(x - other.x, y - other.y)
    operator fun times(value: Double) = RimPoint(x * value, y * value)
}

private infix fun RimPoint.cross(other: RimPoint): Double = x * other.y - y * other.x
private infix fun RimPoint.dot(other: RimPoint): Double = x * other.x + y * other.y
private val RimPoint.norm: Double get() = kotlin.math.sqrt(this dot this)
private val RimPoint.left: RimPoint get() = RimPoint(-y, x)

private data class RimPlane(
    val origin: Vec3,
    val u: Vec3,
    val v: Vec3,
    val tolerance: Double,
) {
    fun project(point: Vec3): RimPoint {
        val offset = point - origin
        return RimPoint(offset * u, offset * v)
    }

    fun lift(point: RimPoint): Vec3 = origin + u * point.x + v * point.y
}

private data class RimStrip(
    val polygon: List<RimPoint>,
    val sources: Set<SourceEdgeOccurrence>,
)

private data class RimSegment(
    val a: RimPoint,
    val b: RimPoint,
    val sources: Set<SourceEdgeOccurrence>,
)

private data class RimNode(
    val point: RimPoint,
)

private data class RimEdge(val a: Int, val b: Int)
private fun rimEdge(a: Int, b: Int) = if (a < b) RimEdge(a, b) else RimEdge(b, a)

/** Computes tessellation-free rim regions for every face at one presentation width. */
fun Polyhedron.resolvedRims(width: Double): List<ResolvedRimGeometry> {
    require(width.isFinite() && width >= 0.0) { "Rim width must be finite and non-negative" }
    val immersedMaximumByKind = mutableMapOf<polyhedra.model.poly.FaceKind, Double>()
    return fs.map { face ->
        val resolved = resolvedFaces[face.id]
        val maximumWidth = if (resolved.sourceBoundarySelfIntersects) {
            immersedMaximumByKind.getOrPut(face.kind) { face.immersedRimMaximum(resolved) }
        } else {
            FaceRim(face).maxRim
        }
        face.resolvedRim(resolved, width, maximumWidth)
    }
}

/** Computes only the requested STL geometry; export does not consume the UI's exact width limit. */
fun Polyhedron.resolvedRimsForExport(width: Double): List<ResolvedRimGeometry> {
    require(width.isFinite() && width >= 0.0) { "Rim width must be finite and non-negative" }
    return fs.map { face ->
        val resolved = resolvedFaces[face.id]
        if (resolved.sourceBoundarySelfIntersects && width > 0.0) {
            face.resolvedRimAtWidth(resolved, width)
        } else {
            face.resolvedRim(resolved, width)
        }
    }
}

/** Builds the Boolean union of source-edge strips, clipped to the face's nonzero-winding fill. */
fun Face.resolvedRim(resolved: ResolvedFaceGeometry, width: Double): ResolvedRimGeometry {
    require(width.isFinite() && width >= 0.0) { "Rim width must be finite and non-negative" }
    val maximumWidth = if (resolved.sourceBoundarySelfIntersects) {
        immersedRimMaximum(resolved)
    } else {
        FaceRim(this).maxRim
    }
    return resolvedRim(resolved, width, maximumWidth)
}

private fun Face.resolvedRim(
    resolved: ResolvedFaceGeometry,
    requestedWidth: Double,
    maximumWidth: Double,
): ResolvedRimGeometry {
    require(resolved.sourceFaceId == id)
    require(maximumWidth.isFinite() && maximumWidth >= 0.0)
    val width = min(requestedWidth, maximumWidth)
    if (width <= 0.0) return ResolvedRimGeometry(id, kind, 0.0, emptyList(), maximumWidth)
    if (!resolved.sourceBoundarySelfIntersects) return simpleRim(width, maximumWidth)
    return resolvedRimAtWidth(resolved, width).copy(maximumWidth = maximumWidth)
}

private fun Face.resolvedRimAtWidth(
    resolved: ResolvedFaceGeometry,
    width: Double,
): ResolvedRimGeometry {
    val plane = rimPlane(resolved)
    val points = resolved.vertices.map { vertex -> plane.project(vertex.position) }
    val cellBoundaries = resolved.cells.map { cell -> cell.boundary.map(points::get) }
    val cellUses = HashMap<RimEdge, MutableList<Pair<Int, Int>>>()
    for (cell in resolved.cells) {
        val boundaryArea = cell.boundary.map(points::get).signedArea()
        for (index in cell.boundary.indices) {
            val first = cell.boundary[index]
            val second = cell.boundary[(index + 1) % cell.boundary.size]
            val (a, b) = if (boundaryArea >= 0.0) first to second else second to first
            cellUses.getOrPut(rimEdge(a, b), ::arrayListOf) += a to b
        }
    }

    val strips = resolved.edges.flatMap { edge ->
        if (edge.provenance.sourceEdgeIds.isEmpty()) return@flatMap emptyList()
        val uses = cellUses[rimEdge(edge.a, edge.b)].orEmpty()
        if (uses.isEmpty()) return@flatMap emptyList()
        val sourceIds = edge.provenance.sourceEdgeIds
        sourceIds.map { sourceSegmentIndex ->
            val occurrence = SourceEdgeOccurrence(id, sourceSegmentIndex)
            val directed = uses.first()
            val a = points[directed.first]
            val b = points[directed.second]
            val direction = b - a
            val length = direction.norm
            require(length > plane.tolerance) { "Resolved face $id has a collapsed rim segment" }
            val inward = (direction * (1.0 / length)).left
            val polygon = if (edge.internalToFill) {
                val offset = inward * (width / 2.0)
                listOf(a - offset, b - offset, b + offset, a + offset)
            } else {
                val offset = inward * width
                listOf(a, b, b + offset, a + offset)
            }
            RimStrip(polygon, setOf(occurrence))
        }
    }

    // Source vertices are joins, while arrangement crossings are ordinary strip intersections.
    val joins = fvs.indices.mapNotNull { sourceVertexIndex ->
        val vertex = plane.project(fvs[sourceVertexIndex])
        val incomingSource = (sourceVertexIndex + fvs.lastIndex) % fvs.size
        val outgoingSource = sourceVertexIndex
        val touching = strips.filter { strip ->
            strip.sources.any { source ->
                source.sourceSegmentIndex == incomingSource || source.sourceSegmentIndex == outgoingSource
            }
        }
        if (touching.size < 2) return@mapNotNull null
        val candidates = touching.flatMap { strip ->
            strip.polygon.filter { point -> (point - vertex).norm <= width * 4.0 + plane.tolerance }
        } + vertex
        val hull = candidates.convexHull(plane.tolerance)
        hull.takeIf { it.size >= 3 }?.let { polygon ->
            RimStrip(
                polygon,
                setOf(SourceEdgeOccurrence(id, incomingSource), SourceEdgeOccurrence(id, outgoingSource)),
            )
        }
    }
    val allStrips = strips + joins
    if (allStrips.isEmpty()) return ResolvedRimGeometry(id, kind, width, emptyList())

    val inputSegments = buildList {
        for (boundary in cellBoundaries) boundary.addSegmentsTo(this, emptySet())
        for (strip in allStrips) strip.polygon.addSegmentsTo(this, strip.sources)
    }
    val splitSegments = inputSegments.splitAtIntersections(plane.tolerance)
    val boundarySegments = splitSegments.selectBoundary(
        plane.tolerance,
        inside = { point ->
            cellBoundaries.any { boundary -> boundary.contains(point, plane.tolerance) } &&
                allStrips.any { strip -> strip.polygon.contains(point, plane.tolerance) }
        },
    )
    val cycles = boundarySegments.toCycles(plane.tolerance, width)
    val positive = cycles.filter { cycle -> cycle.points.signedArea() > 0.0 }
    val negative = cycles.filter { cycle -> cycle.points.signedArea() < 0.0 }
    val regions = positive.sortedByDescending { cycle -> abs(cycle.points.signedArea()) }.map { outer ->
        val holes = negative.filter { hole -> outer.points.contains(hole.points.first(), plane.tolerance) }
            .filter { hole ->
                positive.none { nested ->
                    nested !== outer && abs(nested.points.signedArea()) < abs(outer.points.signedArea()) &&
                        nested.points.contains(hole.points.first(), plane.tolerance)
                }
            }
        val edgeSources = (outer.segmentSources + holes.flatMap(RimCycle2::segmentSources))
            .flatten().distinct().sortedWith(compareBy(
                SourceEdgeOccurrence::sourceFaceId,
                SourceEdgeOccurrence::sourceSegmentIndex,
            ))
        ResolvedRimRegion(
            outer.toModel(plane),
            holes.map { hole -> hole.toModel(plane) },
            edgeSources,
        )
    }
    return ResolvedRimGeometry(id, kind, width, regions)
}

private fun Face.simpleRim(width: Double, maximumWidth: Double): ResolvedRimGeometry {
    val faceRim = FaceRim(this)
    val occurrences = fvs.indices.map { index -> SourceEdgeOccurrence(id, index) }
    val outer = orientedCycle(fvs, occurrences.map(::listOf), counterClockwise = true)
    if (width >= maximumWidth * (1.0 - 1e-9)) {
        return ResolvedRimGeometry(
            id,
            kind,
            width,
            listOf(ResolvedRimRegion(outer, emptyList(), occurrences)),
            maximumWidth,
        )
    }
    val innerVertices = ArrayList<Vec3>()
    val innerSources = ArrayList<List<SourceEdgeOccurrence>>()
    for (index in fvs.indices) {
        val miter = faceRim.rimDir[index] * width
        innerVertices += fvs[index] + miter
        innerSources += listOf(occurrences[index])
    }
    val inner = orientedCycle(innerVertices, innerSources, counterClockwise = false)
    return ResolvedRimGeometry(
        id,
        kind,
        width,
        listOf(ResolvedRimRegion(outer, listOf(inner), occurrences)),
        maximumWidth,
    )
}

private fun Face.orientedCycle(
    vertices: List<Vec3>,
    segmentSources: List<List<SourceEdgeOccurrence>>,
    counterClockwise: Boolean,
): ResolvedRimCycle {
    val isCounterClockwise = projectedArea(vertices) >= 0.0
    if (isCounterClockwise == counterClockwise) {
        return ResolvedRimCycle(
            vertices.map { point -> polyhedra.model.util.MutableVec3(point) },
            segmentSources,
        )
    }
    val reversedVertices = vertices.asReversed()
    val reversedSources = vertices.indices.map { index ->
        segmentSources[(vertices.size - 2 - index + vertices.size) % vertices.size]
    }
    return ResolvedRimCycle(
        reversedVertices.map { point -> polyhedra.model.util.MutableVec3(point) },
        reversedSources,
    )
}

private fun Face.projectedArea(vertices: List<Vec3>): Double = vertices.indices.sumOf { index ->
    val a = vertices[index]
    val b = vertices[(index + 1) % vertices.size]
    (a cross b) * this
} / 2.0

private fun Face.immersedRimMaximum(resolved: ResolvedFaceGeometry): Double {
    val plane = rimPlane(resolved)
    val points = resolved.vertices.map { vertex -> plane.project(vertex.position) }
    val fillArea = resolved.cells.sumOf { cell -> abs(cell.boundary.map(points::get).signedArea()) }
    val scale = maxOf(
        points.maxOf(RimPoint::x) - points.minOf(RimPoint::x),
        points.maxOf(RimPoint::y) - points.minOf(RimPoint::y),
    )
    val areaTolerance = maxOf(plane.tolerance * scale * 64.0, fillArea * 1e-9)
    fun covered(width: Double): Boolean {
        val area = resolvedRimAtWidth(resolved, width).regions.sumOf { region ->
            val outer = region.outer.vertices.map(plane::project).signedArea()
            val holes = region.holes.sumOf { cycle ->
                abs(cycle.vertices.map(plane::project).signedArea())
            }
            abs(outer) - holes
        }
        return fillArea - area <= areaTolerance
    }

    var upper = scale.coerceAtLeast(plane.tolerance)
    repeat(4) {
        if (covered(upper)) return@repeat
        upper *= 2.0
    }
    require(covered(upper)) { "Could not bound the complete-coverage rim width for face $id" }
    var lower = 0.0
    repeat(28) {
        val middle = (lower + upper) / 2.0
        if (covered(middle)) upper = middle else lower = middle
    }
    return upper
}

private fun Face.rimPlane(resolved: ResolvedFaceGeometry): RimPlane {
    val normal = unit
    val axis = when {
        abs(normal.x) <= abs(normal.y) && abs(normal.x) <= abs(normal.z) -> Vec3(1.0, 0.0, 0.0)
        abs(normal.y) <= abs(normal.z) -> Vec3(0.0, 1.0, 0.0)
        else -> Vec3(0.0, 0.0, 1.0)
    }
    val u = (axis cross normal).unit
    val v = (normal cross u).unit
    val origin = resolved.vertices.first().position
    val projected = resolved.vertices.map { point ->
        val offset = point.position - origin
        RimPoint(offset * u, offset * v)
    }
    val scale = maxOf(
        projected.maxOf(RimPoint::x) - projected.minOf(RimPoint::x),
        projected.maxOf(RimPoint::y) - projected.minOf(RimPoint::y),
    )
    return RimPlane(origin, u, v, maxOf(EPS * scale * 16.0, 1e-12 * scale))
}

private fun List<RimPoint>.addSegmentsTo(
    target: MutableList<RimSegment>,
    sources: Set<SourceEdgeOccurrence>,
) {
    for (index in indices) {
        val a = this[index]
        val b = this[(index + 1) % size]
        if ((b - a).norm > 0.0) target += RimSegment(a, b, sources)
    }
}

private fun List<RimSegment>.splitAtIntersections(tolerance: Double): List<RimSegment> {
    val parameters = List(size) { mutableListOf(0.0, 1.0) }
    for (first in indices) for (second in (first + 1) until size) {
        this[first].intersectionParameters(this[second], tolerance)?.let { (firstValues, secondValues) ->
            parameters[first] += firstValues
            parameters[second] += secondValues
        }
    }
    return flatMapIndexed { index, segment ->
        val ordered = parameters[index].map { it.coerceIn(0.0, 1.0) }.sorted()
            .fold(ArrayList<Double>()) { values, value ->
                if (values.isEmpty() || abs(values.last() - value) > tolerance / (segment.b - segment.a).norm) {
                    values += value
                }
                values
            }
        (0 until ordered.lastIndex).mapNotNull { part ->
            val a = segment.a + (segment.b - segment.a) * ordered[part]
            val b = segment.a + (segment.b - segment.a) * ordered[part + 1]
            RimSegment(a, b, segment.sources).takeIf { (b - a).norm > tolerance }
        }
    }
}

private fun RimSegment.intersectionParameters(
    other: RimSegment,
    tolerance: Double,
): Pair<List<Double>, List<Double>>? {
    val r = b - a
    val s = other.b - other.a
    val denominator = r cross s
    val offset = other.a - a
    val scale = maxOf(r.norm, s.norm)
    if (abs(denominator) > tolerance * scale) {
        val t = (offset cross s) / denominator
        val u = (offset cross r) / denominator
        val parameterTolerance = tolerance / scale
        return if (t in -parameterTolerance..(1.0 + parameterTolerance) &&
            u in -parameterTolerance..(1.0 + parameterTolerance)
        ) listOf(t) to listOf(u) else null
    }
    if (abs(offset cross r) > tolerance * scale) return null
    val rr = r dot r
    val ss = s dot s
    val first = listOf(
        ((other.a - a) dot r) / rr,
        ((other.b - a) dot r) / rr,
    ).filter { value -> value in -tolerance / scale..(1.0 + tolerance / scale) }
    val second = listOf(
        ((a - other.a) dot s) / ss,
        ((b - other.a) dot s) / ss,
    ).filter { value -> value in -tolerance / scale..(1.0 + tolerance / scale) }
    return (first + listOf(0.0, 1.0)) to (second + listOf(0.0, 1.0))
}

private fun List<RimSegment>.selectBoundary(
    tolerance: Double,
    inside: (RimPoint) -> Boolean,
): List<RimSegment> {
    val selected = ArrayList<RimSegment>()
    for (segment in this) {
        val direction = segment.b - segment.a
        val length = direction.norm
        val midpoint = (segment.a + segment.b) * 0.5
        val offset = direction.left * (maxOf(tolerance * 8.0, length * 1e-7) / length)
        val leftInside = inside(midpoint + offset)
        val rightInside = inside(midpoint - offset)
        when {
            leftInside && !rightInside -> selected += segment
            rightInside && !leftInside -> selected += RimSegment(segment.b, segment.a, segment.sources)
        }
    }
    return selected
}

private data class RimCycle2(
    val points: List<RimPoint>,
    val segmentSources: List<Set<SourceEdgeOccurrence>>,
) {
    fun toModel(plane: RimPlane) = ResolvedRimCycle(
        points.map { point -> polyhedra.model.util.MutableVec3(plane.lift(point)) },
        segmentSources.map { sources -> sources.sortedBy(SourceEdgeOccurrence::sourceSegmentIndex) },
    )
}

private fun List<RimSegment>.toCycles(tolerance: Double, rimWidth: Double): List<RimCycle2> {
    val nodes = ArrayList<RimNode>()
    val buckets = HashMap<Pair<Long, Long>, MutableList<Int>>()
    fun node(point: RimPoint): Int {
        val key = floor(point.x / tolerance).toLong() to floor(point.y / tolerance).toLong()
        for (dx in -1L..1L) for (dy in -1L..1L) {
            for (candidate in buckets[key.first + dx to key.second + dy].orEmpty()) {
                if ((nodes[candidate].point - point).norm <= tolerance) return candidate
            }
        }
        val index = nodes.size
        nodes += RimNode(point)
        buckets.getOrPut(key, ::arrayListOf) += index
        return index
    }
    val directed = linkedMapOf<Pair<Int, Int>, MutableSet<SourceEdgeOccurrence>>()
    for (segment in this) {
        val a = node(segment.a)
        val b = node(segment.b)
        if (a == b) continue
        directed.getOrPut(a to b, ::linkedSetOf) += segment.sources
    }
    val outgoing = directed.keys.groupBy({ it.first }, { it.second })
    val incoming = directed.keys.groupBy({ it.second }, { it.first })
    require(
        outgoing.keys == incoming.keys &&
            outgoing.values.all { it.size == 1 } && incoming.values.all { it.size == 1 },
    ) {
        val sources = outgoing.keys - incoming.keys
        val sinks = incoming.keys - outgoing.keys
        "Resolved rim boundary is not a collection of manifold cycles " +
            "(sources=${sources.map { nodes[it].point }}, sinks=${sinks.map { nodes[it].point }})"
    }
    val remaining = directed.keys.toMutableSet()
    val cycles = ArrayList<RimCycle2>()
    while (remaining.isNotEmpty()) {
        val start = remaining.minWith(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
        val points = ArrayList<RimPoint>()
        val sources = ArrayList<Set<SourceEdgeOccurrence>>()
        var edge = start
        do {
            require(remaining.remove(edge)) { "Resolved rim cycle does not close" }
            points += nodes[edge.first].point
            sources += directed.getValue(edge)
            edge = edge.second to outgoing.getValue(edge.second).single()
        } while (edge != start)
        simplifyCycle(points, sources, tolerance)
        miterShortJoins(points, sources, rimWidth, tolerance)
        if (points.size >= 3 && abs(points.signedArea()) > tolerance * tolerance) {
            cycles += RimCycle2(points, sources)
        }
    }
    return cycles
}

/**
 * Replaces the tiny stair-step left where several clipped strip caps meet with
 * the exact intersection of the two adjacent offset edges. The distance guard
 * keeps this local: genuinely short polygon features and acute unbounded miters
 * remain unchanged.
 */
private fun miterShortJoins(
    points: MutableList<RimPoint>,
    sources: MutableList<Set<SourceEdgeOccurrence>>,
    rimWidth: Double,
    tolerance: Double,
) {
    if (points.size <= 3 || rimWidth <= tolerance) return
    val shortLimit = rimWidth * 1.05 + tolerance
    val longEdges = points.indices.filter { index ->
        (points[(index + 1) % points.size] - points[index]).norm > shortLimit
    }
    if (longEdges.size < 3 || longEdges.size == points.size) return

    val replacementPoints = ArrayList<RimPoint>(longEdges.size)
    val replacementSources = ArrayList<Set<SourceEdgeOccurrence>>(longEdges.size)
    val maximumMiter = rimWidth * 4.0 + tolerance
    for (position in longEdges.indices) {
        val current = longEdges[position]
        val previous = longEdges[(position + longEdges.lastIndex) % longEdges.size]
        val currentStart = points[current]
        val previousEnd = points[(previous + 1) % points.size]
        val adjacent = (previous + 1) % points.size == current
        val point = if (adjacent) {
            currentStart
        } else {
            val adjacentSources = sources[previous] + sources[current]
            if (sources[previous].size != 1 || sources[current].size != 1 || adjacentSources.size != 2) return
            var shortEdge = (previous + 1) % points.size
            while (shortEdge != current) {
                if (sources[shortEdge].isEmpty() || !adjacentSources.containsAll(sources[shortEdge])) return
                shortEdge = (shortEdge + 1) % points.size
            }
            lineIntersection(
                points[previous],
                previousEnd,
                currentStart,
                points[(current + 1) % points.size],
                tolerance,
            ) ?: return
        }
        if ((point - previousEnd).norm > maximumMiter || (point - currentStart).norm > maximumMiter) return
        replacementPoints += point
        replacementSources += sources[current]
    }
    if (replacementPoints.signedArea() * points.signedArea() <= 0.0) return
    points.clear()
    points += replacementPoints
    sources.clear()
    sources += replacementSources
}

private fun lineIntersection(
    firstA: RimPoint,
    firstB: RimPoint,
    secondA: RimPoint,
    secondB: RimPoint,
    tolerance: Double,
): RimPoint? {
    val first = firstB - firstA
    val second = secondB - secondA
    val denominator = first cross second
    if (abs(denominator) <= tolerance * maxOf(first.norm, second.norm)) return null
    val parameter = ((secondA - firstA) cross second) / denominator
    return firstA + first * parameter
}

private fun simplifyCycle(
    points: MutableList<RimPoint>,
    sources: MutableList<Set<SourceEdgeOccurrence>>,
    tolerance: Double,
) {
    var changed = true
    while (changed && points.size > 3) {
        changed = false
        for (index in points.indices) {
            val previousIndex = (index + points.lastIndex) % points.size
            val previous = points[previousIndex]
            val current = points[index]
            val next = points[(index + 1) % points.size]
            val incoming = current - previous
            val outgoing = next - current
            if (abs(incoming cross outgoing) <= tolerance * maxOf(incoming.norm, outgoing.norm) &&
                incoming dot outgoing > 0.0
            ) {
                sources[previousIndex] = sources[previousIndex] + sources[index]
                points.removeAt(index)
                sources.removeAt(index)
                changed = true
                break
            }
        }
    }
}

private fun List<RimPoint>.signedArea(): Double = indices.sumOf { index ->
    this[index] cross this[(index + 1) % size]
} / 2.0

private fun List<RimPoint>.contains(point: RimPoint, tolerance: Double): Boolean {
    var inside = false
    for (index in indices) {
        val a = this[index]
        val b = this[(index + 1) % size]
        val edge = b - a
        if (abs(edge cross (point - a)) <= tolerance * edge.norm &&
            point.x in minOf(a.x, b.x) - tolerance..maxOf(a.x, b.x) + tolerance &&
            point.y in minOf(a.y, b.y) - tolerance..maxOf(a.y, b.y) + tolerance
        ) return true
        if ((a.y > point.y) != (b.y > point.y)) {
            val crossingX = a.x + (b.x - a.x) * (point.y - a.y) / (b.y - a.y)
            if (crossingX > point.x) inside = !inside
        }
    }
    return inside
}

private fun List<RimPoint>.convexHull(tolerance: Double): List<RimPoint> {
    val sorted = sortedWith(compareBy(RimPoint::x, RimPoint::y)).fold(ArrayList<RimPoint>()) { result, point ->
        if (result.isEmpty() || (result.last() - point).norm > tolerance) result += point
        result
    }
    if (sorted.size <= 2) return sorted
    fun half(points: List<RimPoint>): List<RimPoint> {
        val result = ArrayList<RimPoint>()
        for (point in points) {
            while (result.size >= 2 &&
                ((result.last() - result[result.lastIndex - 1]) cross (point - result.last())) <= tolerance
            ) result.removeLast()
            result += point
        }
        return result
    }
    return (half(sorted).dropLast(1) + half(sorted.asReversed()).dropLast(1))
}
