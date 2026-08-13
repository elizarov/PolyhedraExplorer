package polyhedra.model.poly

import polyhedra.model.api.ResolvedElementProvenance
import polyhedra.model.api.SourceSegmentPoint
import polyhedra.model.util.EPS
import polyhedra.model.util.MutableVec3
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.plus
import polyhedra.model.util.times
import polyhedra.model.util.unit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.round

private data class Point2(val x: Double, val y: Double) {
    operator fun plus(other: Point2) = Point2(x + other.x, y + other.y)
    operator fun minus(other: Point2) = Point2(x - other.x, y - other.y)
    operator fun times(scale: Double) = Point2(x * scale, y * scale)
}

private infix fun Point2.cross(other: Point2): Double = x * other.y - y * other.x
private infix fun Point2.dot(other: Point2): Double = x * other.x + y * other.y
private val Point2.norm: Double get() = kotlin.math.sqrt(this dot this)

private data class ProjectedBoundary(
    val points: List<Point2>,
    val origin: Vec3,
    val u: Vec3,
    val v: Vec3,
    val linearTolerance: Double,
    val areaTolerance: Double,
)

private data class ArrangementNode(
    val point: Point2,
    val position: Vec3,
    val sourceVertices: MutableSet<Int> = linkedSetOf(),
    val segmentPoints: MutableMap<Pair<Int, Int>, Double> = linkedMapOf(),
)

private data class SegmentNode(val parameter: Double, val node: Int)
private data class Piece(val a: Int, val b: Int, val sourceSegment: Int)
private data class EdgeKey(val a: Int, val b: Int)
private fun edgeKey(a: Int, b: Int) = if (a < b) EdgeKey(a, b) else EdgeKey(b, a)

/**
 * Resolves one face boundary into simple nonzero-winding cells without changing abstract F/E/V.
 * Simple non-planar faces retain their existing deterministic tessellation. A self-crossing
 * boundary must be planar, because lifting crossings from an average projection is undefined.
 */
fun resolveFaceGeometry(face: Face): ResolvedFaceGeometry {
    val projection = face.projectBoundary()
    val intersections = projection.findIntersections(face)
    if (intersections.isEmpty()) return face.directResolvedGeometry()
    require(face.isPlanar) { "Self-intersecting face ${face.id} is not planar" }
    return projection.buildArrangement(face, intersections)
}

private data class BoundaryIntersection(
    val firstSegment: Int,
    val firstParameter: Double,
    val secondSegment: Int,
    val secondParameter: Double,
    val point: Point2,
)

private fun Face.projectBoundary(): ProjectedBoundary {
    val normal = this.unit
    require(normal.norm > EPS) { "Face $id has no well-defined normal" }
    val axis = when {
        abs(normal.x) <= abs(normal.y) && abs(normal.x) <= abs(normal.z) -> Vec3(1.0, 0.0, 0.0)
        abs(normal.y) <= abs(normal.z) -> Vec3(0.0, 1.0, 0.0)
        else -> Vec3(0.0, 0.0, 1.0)
    }
    val u = (axis cross normal).unit
    val v = (normal cross u).unit
    val origin = fvs.first()
    val points = fvs.map { point ->
        val offset = point - origin
        Point2(offset * u, offset * v)
    }
    val scale = maxOf(
        points.maxOf(Point2::x) - points.minOf(Point2::x),
        points.maxOf(Point2::y) - points.minOf(Point2::y),
    )
    require(scale.isFinite() && scale > 0.0) { "Face $id has no projected extent" }
    val linearTolerance = EPS * scale * 16.0
    return ProjectedBoundary(points, origin, u, v, linearTolerance, linearTolerance * scale)
}

private fun ProjectedBoundary.findIntersections(face: Face): List<BoundaryIntersection> {
    data class Bounds(val segment: Int, val minX: Double, val maxX: Double, val minY: Double, val maxY: Double)
    val bounds = points.indices.map { index ->
        val a = points[index]
        val b = points[(index + 1) % points.size]
        Bounds(index, minOf(a.x, b.x), maxOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.y, b.y))
    }.sortedWith(compareBy(Bounds::minX, Bounds::segment))
    val result = ArrayList<BoundaryIntersection>()
    for (firstPosition in bounds.indices) {
        val first = bounds[firstPosition]
        for (secondPosition in (firstPosition + 1) until bounds.size) {
            val second = bounds[secondPosition]
            if (second.minX > first.maxX + linearTolerance) break
            if (second.minY > first.maxY + linearTolerance || first.minY > second.maxY + linearTolerance) continue
            val adjacent = areAdjacent(first.segment, second.segment, points.size)
            segmentIntersection(first.segment, second.segment, adjacent)?.let(result::add)
        }
    }
    return result.sortedWith(compareBy(
        BoundaryIntersection::firstSegment,
        BoundaryIntersection::secondSegment,
        BoundaryIntersection::firstParameter,
    ))
}

private fun areAdjacent(first: Int, second: Int, size: Int): Boolean =
    first == second || (first + 1) % size == second || (second + 1) % size == first

private fun ProjectedBoundary.segmentIntersection(
    first: Int,
    second: Int,
    adjacent: Boolean,
): BoundaryIntersection? {
    val a = points[first]
    val b = points[(first + 1) % points.size]
    val c = points[second]
    val d = points[(second + 1) % points.size]
    val r = b - a
    val s = d - c
    val denominator = r cross s
    val ca = c - a
    val scale = maxOf(r.norm, s.norm, 1.0)
    if (abs(denominator) <= areaTolerance) {
        if (abs(ca cross r) > areaTolerance) return null
        // Adjacent source edges are one continuous boundary feature. Existing transforms may
        // generate a collinear reversal at their shared corner; it is handled by the ordinary
        // simple-face tessellator rather than classified as an unrelated overlapping edge.
        if (adjacent) return null
        val rr = r dot r
        require(rr > linearTolerance * linearTolerance)
        val t0 = (ca dot r) / rr
        val t1 = ((d - a) dot r) / rr
        val overlapStart = maxOf(0.0, minOf(t0, t1))
        val overlapEnd = minOf(1.0, maxOf(t0, t1))
        if (overlapEnd < overlapStart - linearTolerance / scale) return null
        require(overlapEnd - overlapStart <= linearTolerance / scale) {
            "Face boundary has overlapping segments $first and $second " +
                "($a-$b and $c-$d; overlap=$overlapStart..$overlapEnd, tolerance=$linearTolerance)"
        }
        val t = ((overlapStart + overlapEnd) / 2.0).coerceIn(0.0, 1.0)
        val point = a + r * t
        val ss = s dot s
        val u = if (ss == 0.0) 0.0 else ((point - c) dot s) / ss
        return BoundaryIntersection(first, t, second, u.coerceIn(0.0, 1.0), point)
    }
    val t = (ca cross s) / denominator
    val u = (ca cross r) / denominator
    val parameterTolerance = linearTolerance / scale
    if (t !in -parameterTolerance..(1.0 + parameterTolerance) ||
        u !in -parameterTolerance..(1.0 + parameterTolerance)
    ) return null
    val tc = t.coerceIn(0.0, 1.0)
    val uc = u.coerceIn(0.0, 1.0)
    if (adjacent && (tc <= parameterTolerance || tc >= 1.0 - parameterTolerance) &&
        (uc <= parameterTolerance || uc >= 1.0 - parameterTolerance)
    ) return null
    return BoundaryIntersection(first, tc, second, uc, a + r * tc)
}

private fun Face.directResolvedGeometry(): ResolvedFaceGeometry {
    val vertices = fvs.map { vertex ->
        ResolvedFaceVertex(
            MutableVec3(vertex),
            ResolvedElementProvenance(
                sourceVertexIds = listOf(vertex.id),
                sourceFaceIds = listOf(id),
            ),
        )
    }
    val triangles = triangles.map { triangle ->
        ResolvedFaceTriangle(triangle.a, triangle.b, triangle.c, sourceCellId = 0)
    }
    val cell = ResolvedFaceCell(0, winding = if (signedProjectedArea() >= 0.0) 1 else -1, fvs.indices.toList(), triangles)
    val edges = fvs.indices.map { index ->
        ResolvedFaceEdge(
            index,
            (index + 1) % fvs.size,
            ResolvedElementProvenance(sourceFaceIds = listOf(id), sourceEdgeIds = listOf(index)),
            internalToFill = false,
        )
    }
    return ResolvedFaceGeometry(id, kind, sourceBoundarySelfIntersects = false, vertices, listOf(cell), edges)
}

private fun Face.signedProjectedArea(): Double {
    val projection = projectBoundary().points
    return projection.indices.sumOf { index ->
        projection[index] cross projection[(index + 1) % projection.size]
    }
}

private fun ProjectedBoundary.buildArrangement(
    face: Face,
    intersections: List<BoundaryIntersection>,
): ResolvedFaceGeometry {
    val nodes = ArrayList<ArrangementNode>()
    val buckets = HashMap<Pair<Long, Long>, MutableList<Int>>()
    fun bucket(point: Point2): Pair<Long, Long> =
        floor(point.x / linearTolerance).toLong() to floor(point.y / linearTolerance).toLong()
    fun nodeFor(point: Point2): Int {
        val key = bucket(point)
        for (dx in -1L..1L) for (dy in -1L..1L) {
            for (candidate in buckets[key.first + dx to key.second + dy].orEmpty()) {
                if ((nodes[candidate].point - point).norm <= linearTolerance) return candidate
            }
        }
        val position = origin + u * point.x + v * point.y
        val index = nodes.size
        nodes += ArrangementNode(point, position)
        buckets.getOrPut(key, ::arrayListOf) += index
        return index
    }

    val segmentNodes = List(points.size) { mutableListOf<SegmentNode>() }
    for (index in points.indices) {
        val node = nodeFor(points[index])
        nodes[node].sourceVertices += face.fvs[index].id
        segmentNodes[index] += SegmentNode(0.0, node)
        segmentNodes[(index + points.lastIndex) % points.size] += SegmentNode(1.0, node)
    }
    for (intersection in intersections) {
        val node = nodeFor(intersection.point)
        nodes[node].segmentPoints[face.id to intersection.firstSegment] =
            canonicalParameter(intersection.firstParameter)
        nodes[node].segmentPoints[face.id to intersection.secondSegment] =
            canonicalParameter(intersection.secondParameter)
        segmentNodes[intersection.firstSegment] += SegmentNode(intersection.firstParameter, node)
        segmentNodes[intersection.secondSegment] += SegmentNode(intersection.secondParameter, node)
    }

    val pieces = ArrayList<Piece>()
    for (segment in segmentNodes.indices) {
        val ordered = segmentNodes[segment]
            .sortedWith(compareBy(SegmentNode::parameter, SegmentNode::node))
            .distinctBy(SegmentNode::node)
        for (index in 0 until ordered.lastIndex) {
            val a = ordered[index].node
            val b = ordered[index + 1].node
            if (a != b && (nodes[a].point - nodes[b].point).norm > linearTolerance) {
                pieces += Piece(a, b, segment)
            }
        }
    }
    val piecesByEdge = pieces.groupBy { piece -> edgeKey(piece.a, piece.b) }
    require(piecesByEdge.values.all { it.size == 1 }) { "Face boundary contains an overlapping segment" }

    val neighbors = List(nodes.size) { mutableListOf<Int>() }
    for (piece in pieces) {
        neighbors[piece.a] += piece.b
        neighbors[piece.b] += piece.a
    }
    for (node in neighbors.indices) {
        neighbors[node].sortBy { target ->
            val direction = nodes[target].point - nodes[node].point
            atan2(direction.y, direction.x)
        }
    }

    val visited = HashSet<Pair<Int, Int>>()
    val boundaries = ArrayList<List<Int>>()
    for (piece in pieces) for ((start, next) in listOf(piece.a to piece.b, piece.b to piece.a)) {
        if (!visited.add(start to next)) continue
        val boundary = ArrayList<Int>()
        var a = start
        var b = next
        boundary += a
        while (b != start) {
            boundary += b
            val outgoing = neighbors[b]
            val reverseIndex = outgoing.indexOf(a)
            require(reverseIndex >= 0)
            val c = outgoing[(reverseIndex + outgoing.size - 1) % outgoing.size]
            a = b
            b = c
            require(visited.add(a to b)) { "Face arrangement contains a non-closing walk" }
            require(boundary.size <= pieces.size * 2) { "Face arrangement walk exceeds its edge count" }
        }
        val area = boundary.signedArea(nodes)
        if (area > areaTolerance) boundaries += boundary.canonicalRotation()
    }

    val retained = boundaries.mapNotNull { boundary ->
        val sample = boundary.interiorSample(nodes, linearTolerance, areaTolerance)
        val winding = windingAt(sample)
        if (winding == 0) null else boundary to winding
    }.sortedWith { first, second -> compareIndexLists(first.first, second.first) }

    val retainedEdgeCounts = retained
        .flatMap { (boundary, _) -> boundary.indices.map { index -> edgeKey(boundary[index], boundary[(index + 1) % boundary.size]) } }
        .groupingBy { it }
        .eachCount()
    val cells = retained.mapIndexed { cellId, (boundary, winding) ->
        ResolvedFaceCell(
            id = cellId,
            winding = winding,
            boundary = boundary,
            triangles = boundary.triangulate(nodes, areaTolerance).map { (a, b, c) ->
                ResolvedFaceTriangle(a, b, c, cellId)
            },
        )
    }
    val edges = piecesByEdge.entries.sortedWith(compareBy({ it.key.a }, { it.key.b })).map { (key, value) ->
        val sourceSegments = value.map(Piece::sourceSegment).distinct().sorted()
        ResolvedFaceEdge(
            key.a,
            key.b,
            ResolvedElementProvenance(
                sourceFaceIds = listOf(face.id),
                sourceEdgeIds = sourceSegments,
            ),
            internalToFill = retainedEdgeCounts.getOrElse(key) { 0 } == 2,
        )
    }
    val resolvedVertices = nodes.map { node ->
        ResolvedFaceVertex(
            MutableVec3(node.position),
            ResolvedElementProvenance(
                sourceVertexIds = node.sourceVertices.sorted(),
                sourceFaceIds = listOf(face.id),
                sourceSegmentPoints = node.segmentPoints.entries.map { (key, parameter) ->
                    SourceSegmentPoint(key.first, key.second, parameter)
                }.sortedWith(compareBy(SourceSegmentPoint::sourceFaceId, SourceSegmentPoint::sourceSegmentIndex)),
            ),
        )
    }
    require(cells.isNotEmpty()) { "Face ${face.id} has no nonzero-winding cells" }
    return ResolvedFaceGeometry(
        face.id,
        face.kind,
        sourceBoundarySelfIntersects = true,
        resolvedVertices,
        cells,
        edges,
    )
}

private fun canonicalParameter(value: Double): Double =
    round(value.coerceIn(0.0, 1.0) * 1e12) / 1e12

private fun List<Int>.signedArea(nodes: List<ArrangementNode>): Double = indices.sumOf { index ->
    nodes[this[index]].point cross nodes[this[(index + 1) % size]].point
} / 2.0

private fun List<Int>.canonicalRotation(): List<Int> {
    val start = indices.minWithOrNull { first, second ->
        for (offset in indices) {
            val comparison = this[(first + offset) % size].compareTo(this[(second + offset) % size])
            if (comparison != 0) return@minWithOrNull comparison
        }
        0
    } ?: 0
    return List(size) { offset -> this[(start + offset) % size] }
}

private fun compareIndexLists(first: List<Int>, second: List<Int>): Int {
    for (index in 0 until minOf(first.size, second.size)) {
        val comparison = first[index].compareTo(second[index])
        if (comparison != 0) return comparison
    }
    return first.size.compareTo(second.size)
}

private fun List<Int>.interiorSample(
    nodes: List<ArrangementNode>,
    tolerance: Double,
    areaTolerance: Double,
): Point2 {
    val center = fold(Point2(0.0, 0.0)) { sum, node -> sum + nodes[node].point } * (1.0 / size)
    if (containsPoint(center, nodes, tolerance)) return center
    // The centroid of an ear lies strictly in the cell and remains reliable for very narrow cells
    // where any fixed normal offset can still be classified as boundary under scale tolerance.
    triangulate(nodes, areaTolerance).firstOrNull()?.let { (a, b, c) ->
        return (nodes[a].point + nodes[b].point + nodes[c].point) * (1.0 / 3.0)
    }
    for (index in indices) {
        val a = nodes[this[index]].point
        val b = nodes[this[(index + 1) % size]].point
        val edge = b - a
        if (edge.norm <= tolerance) continue
        val midpoint = (a + b) * 0.5
        val inward = Point2(-edge.y / edge.norm, edge.x / edge.norm)
        val sample = midpoint + inward * maxOf(tolerance * 8.0, edge.norm * 1e-7)
        if (containsPoint(sample, nodes, tolerance)) return sample
    }
    error("Cannot find an interior point for arrangement cell")
}

private fun List<Int>.containsPoint(point: Point2, nodes: List<ArrangementNode>, tolerance: Double): Boolean {
    var inside = false
    for (index in indices) {
        val a = nodes[this[index]].point
        val b = nodes[this[(index + 1) % size]].point
        if (abs((b - a) cross (point - a)) <= tolerance * maxOf((b - a).norm, 1.0) &&
            point.x in minOf(a.x, b.x) - tolerance..maxOf(a.x, b.x) + tolerance &&
            point.y in minOf(a.y, b.y) - tolerance..maxOf(a.y, b.y) + tolerance
        ) return false
        if ((a.y > point.y) != (b.y > point.y)) {
            val crossingX = a.x + (b.x - a.x) * (point.y - a.y) / (b.y - a.y)
            if (crossingX > point.x) inside = !inside
        }
    }
    return inside
}

private fun ProjectedBoundary.windingAt(point: Point2): Int {
    var winding = 0
    for (index in points.indices) {
        val a = points[index]
        val b = points[(index + 1) % points.size]
        val side = (b - a) cross (point - a)
        when {
            a.y <= point.y && point.y < b.y && side > areaTolerance -> winding++
            b.y <= point.y && point.y < a.y && side < -areaTolerance -> winding--
        }
    }
    return winding
}

private fun List<Int>.triangulate(
    nodes: List<ArrangementNode>,
    areaTolerance: Double,
): List<Triple<Int, Int, Int>> {
    val remaining = toMutableList()
    var changed: Boolean
    do {
        changed = false
        if (remaining.size <= 3) break
        for (index in remaining.indices) {
            val previous = remaining[(index + remaining.size - 1) % remaining.size]
            val current = remaining[index]
            val next = remaining[(index + 1) % remaining.size]
            if (abs((nodes[current].point - nodes[previous].point) cross
                    (nodes[next].point - nodes[current].point)) <= areaTolerance
            ) {
                remaining.removeAt(index)
                changed = true
                break
            }
        }
    } while (changed)
    require(remaining.size >= 3)
    val triangles = ArrayList<Triple<Int, Int, Int>>()
    while (remaining.size > 3) {
        var clipped = false
        for (index in remaining.indices) {
            val previous = remaining[(index + remaining.size - 1) % remaining.size]
            val current = remaining[index]
            val next = remaining[(index + 1) % remaining.size]
            val a = nodes[previous].point
            val b = nodes[current].point
            val c = nodes[next].point
            if ((b - a) cross (c - b) <= areaTolerance) continue
            if (remaining.any { candidate ->
                    candidate !in setOf(previous, current, next) &&
                        nodes[candidate].point.insideTriangle(a, b, c, areaTolerance)
                }
            ) continue
            triangles += Triple(previous, current, next)
            remaining.removeAt(index)
            clipped = true
            break
        }
        require(clipped) { "Arrangement cell cannot be triangulated" }
    }
    triangles += Triple(remaining[0], remaining[1], remaining[2])
    return triangles
}

private fun Point2.insideTriangle(a: Point2, b: Point2, c: Point2, tolerance: Double): Boolean =
    (b - a) cross (this - a) >= -tolerance &&
        (c - b) cross (this - b) >= -tolerance &&
        (a - c) cross (this - c) >= -tolerance
