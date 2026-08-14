package polyhedra.core.transform

import polyhedra.core.poly.polyhedron
import polyhedra.core.poly.scaled
import polyhedra.core.poly.signedVolume
import polyhedra.core.poly.validateRenderableImmersion
import polyhedra.core.poly.validateProperGeometry
import polyhedra.core.util.runSynchronously
import polyhedra.model.poly.FEV
import polyhedra.model.poly.Edge
import polyhedra.model.poly.Face
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.Scale
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.fev
import polyhedra.model.util.Plane
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.averagePlane
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.planeIntersection
import polyhedra.model.util.plus
import polyhedra.model.util.times
import polyhedra.model.util.unit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToLong

internal enum class ConstellationOperation { Greaten, Stellate }

internal data class StellationCandidate(
    val poly: Polyhedron,
    val fev: FEV = poly.fev(),
    val stratum: Int? = null,
)

private class CompoundStellationException(componentCount: Int) : IllegalArgumentException(
    "Main-line candidate is a compound with $componentCount disconnected surface components",
)

private data class ConstellationPlane(
    val face: Face,
    val normal: Vec3,
    val distance: Double,
    val center: Vec3,
    val u: Vec3,
    val v: Vec3,
    val sourceRadius: Double,
) : Plane {
    override val x: Double get() = normal.x
    override val y: Double get() = normal.y
    override val z: Double get() = normal.z
    override val d: Double get() = distance
}

private data class RingKey(val size: Int, val radiusBin: Long)
private data class FaceCircuit(val points: List<Vec3>, val step: Int, val radius: Double)
private data class CandidateKey(val ring: RingKey, val step: Int)
private data class DiagramEdge(val a: Int, val b: Int)
private data class ArrangementCell(val outsidePlanes: List<Int>) {
    val power: Int get() = outsidePlanes.size
}
private data class DiagramFacet(
    val points: List<Vec3>,
    val innerCell: ArrangementCell,
    val outerCell: ArrangementCell,
) {
    val power: Int
        get() {
            check(outerCell.power == innerCell.power + 1)
            return innerCell.power
        }
}
private data class PlaneDiagram(val plane: ConstellationPlane, val facets: List<DiagramFacet>)

private const val CONSTELLATION_EPS = 2e-7
private const val MAX_CONSTELLATION_RADIUS = 1e6
private const val CANDIDATE_CACHE_SIZE = 16

private data class NormalizedFaceSignature(
    val kind: Int,
    val plane: List<Long>,
    val boundary: List<Triple<Long, Long, Long>>,
)
private data class CandidateCacheKey(
    val faces: List<NormalizedFaceSignature>,
    val operation: ConstellationOperation,
)
private val candidateCache = LinkedHashMap<CandidateCacheKey, List<StellationCandidate>>()

internal fun Polyhedron.stellationCandidates(
    operation: ConstellationOperation,
): List<StellationCandidate> = runSynchronously { stellationCandidatesAsync(operation) }

internal suspend fun Polyhedron.stellationCandidatesAsync(
    operation: ConstellationOperation,
): List<StellationCandidate> {
    val key = CandidateCacheKey(normalizedConstellationSignature(), operation)
    candidateCache[key]?.let { cached ->
        candidateCache.remove(key)
        candidateCache[key] = cached
        return cached
    }
    val result = buildStellationCandidates(operation).map { candidate ->
        candidate.copy(poly = candidate.poly.scaled(Scale.Circumradius))
    }
    candidateCache[key] = result
    while (candidateCache.size > CANDIDATE_CACHE_SIZE) {
        candidateCache.remove(candidateCache.keys.first())
    }
    return result
}

private suspend fun Polyhedron.buildStellationCandidates(
    operation: ConstellationOperation,
): List<StellationCandidate> {
    require(fs.all(Face::isPlanar)) { "Face-plane constellation requires planar source faces" }
    val scale = circumradius.coerceAtLeast(1.0)
    val tolerance = CONSTELLATION_EPS * scale
    val planes = fs.map { face -> face.toConstellationPlane(tolerance) }
    require(planes.none { plane -> abs(plane.distance) <= tolerance }) {
        "Face-plane constellation contains a plane through the symmetry center"
    }
    require(planes.indices.all { first ->
        (first + 1 until planes.size).none { second -> planes[first].coincidesWith(planes[second], tolerance) }
    }) { "Face-plane constellation contains coincident source planes" }

    if (operation == ConstellationOperation.Greaten) return buildGenericGreateningCandidates(tolerance)
    return buildMainLineStellations(planes, tolerance)
}

private suspend fun Polyhedron.buildArrangementCircuitStellations(
    planes: List<ConstellationPlane>,
    tolerance: Double,
): List<StellationCandidate> {
    val scale = circumradius.coerceAtLeast(1.0)
    val pointsByPlane = List(planes.size) { arrayListOf<Vec3>() }
    for (first in 0 until planes.size - 2) {
        for (second in first + 1 until planes.size - 1) {
            for (third in second + 1 until planes.size) {
                val determinant = planes[first].normal * (planes[second].normal cross planes[third].normal)
                if (abs(determinant) <= CONSTELLATION_EPS) continue
                val point = planeIntersection(planes[first], planes[second], planes[third])
                if (!point.isFinite() || point.norm > scale * MAX_CONSTELLATION_RADIUS) continue
                pointsByPlane[first].addDistinct(point, tolerance)
                pointsByPlane[second].addDistinct(point, tolerance)
                pointsByPlane[third].addDistinct(point, tolerance)
            }
        }
    }

    val circuitsByPlane = planes.indices.map { index ->
        planes[index].circuits(pointsByPlane[index], tolerance)
    }
    val commonKeys = circuitsByPlane
        .map { circuits -> circuits.keys }
        .reduceOrNull(Set<CandidateKey>::intersect)
        .orEmpty()
    val constellationCandidates = commonKeys
        .sortedWith(compareBy<CandidateKey>({ it.ring.radiusBin }, { it.step }, { it.ring.size }))
        .mapNotNull { candidateKey ->
            val circuits = circuitsByPlane.map { planeCircuits -> planeCircuits.getValue(candidateKey) }
            if (planes.indices.any { index ->
                circuits[index].radius <= planes[index].sourceRadius + tolerance
            }) return@mapNotNull null
            runCatching { buildCandidate(planes, circuits, tolerance) }
                .getOrNull()
                ?.takeUnless { candidate -> candidate.sameGeometryAs(this, tolerance) }
                ?.let(::StellationCandidate)
        }
        .distinctBy { candidate -> candidate.poly.coordinateSignature(tolerance) to candidate.poly.edgeSignature(tolerance) }

    return constellationCandidates
        .distinctBy { candidate -> candidate.poly.coordinateSignature(tolerance) to candidate.poly.edgeSignature(tolerance) }
        .sortedWith(compareBy(
        { candidate -> candidate.poly.meanFaceCircuitRadius() },
        { candidate -> candidate.fev.f },
        { candidate -> candidate.fev.e },
        { candidate -> candidate.fev.v },
        ))
}

/**
 * Builds the main line directly from the arrangement of the source face planes. A point in an
 * arrangement cell has one positive half-space bit for every source plane crossed from the convex
 * core. Its power is therefore its exact graph distance from the core cell. The boundary between
 * powers [power] and [power] + 1 consists of the diagram facets of power [power].
 */
private suspend fun Polyhedron.buildMainLineStellations(
    planes: List<ConstellationPlane>,
    tolerance: Double,
): List<StellationCandidate> {
    val diagrams = planes.indices.map { planeIndex ->
        buildPlaneDiagram(planeIndex, planes, tolerance)
    }
    val commonPowers = diagrams
        .map { diagram -> diagram.facets.mapTo(linkedSetOf(), DiagramFacet::power) }
        .reduceOrNull(Set<Int>::intersect)
        .orEmpty()
        .filter { power -> power > 0 }
        .sorted()
    val physicalByPower = commonPowers.mapNotNull { power ->
        runCatching { buildMainLinePhysicalBoundary(diagrams, power, tolerance) }
            .getOrNull()
            ?.let { physical -> power to physical }
    }
    val resolvedSource = runCatching { resolved(null) }.getOrNull()
    val sourcePower = resolvedSource?.let { source ->
        physicalByPower.lastOrNull { (_, physical) -> source.matchesPhysicalBoundary(physical, tolerance) }?.first
    } ?: 0
    val circuitCandidates = buildArrangementCircuitStellations(planes, tolerance)
    val resolvedCircuits = circuitCandidates.mapNotNull { candidate ->
        runCatching { candidate.poly.resolved(null) }.getOrNull()?.let { resolved -> candidate to resolved }
    }
    return physicalByPower.mapNotNull { (power, physical) ->
        if (power <= sourcePower) return@mapNotNull null
        val matchedCircuit = resolvedCircuits.firstOrNull { (_, resolved) ->
            resolved.matchesPhysicalBoundary(physical, tolerance)
        }?.first
        val sourceResult = runCatching { buildMainLineCandidate(diagrams, power, tolerance) }
        val sourceCandidate = sourceResult.getOrNull()
        if (sourceResult.exceptionOrNull() is CompoundStellationException) {
            return@mapNotNull null
        }
        val candidate: Polyhedron = matchedCircuit?.poly ?: sourceCandidate ?: physical
        candidate.takeUnless { result -> result.sameGeometryAs(this, tolerance) }
            ?.let { result -> StellationCandidate(result, stratum = power) }
    }.distinctBy { candidate ->
        candidate.poly.coordinateSignature(tolerance) to candidate.poly.edgeSignature(tolerance)
    }
}

private fun Polyhedron.matchesPhysicalBoundary(other: Polyhedron, tolerance: Double): Boolean {
    val scale = maxOf(circumradius, other.circumradius, 1.0)
    if (abs(circumradius - other.circumradius) > tolerance * 32.0) return false
    if (abs(signedVolume() - other.signedVolume()) > tolerance * scale * scale * 64.0) return false
    if (vs.size != other.vs.size) return false
    val vertexTolerance = tolerance * 32.0
    return vs.all { vertex -> other.vs.any { candidate -> (candidate - vertex).norm <= vertexTolerance } } &&
        other.vs.all { vertex -> vs.any { candidate -> (candidate - vertex).norm <= vertexTolerance } }
}

private fun buildPlaneDiagram(
    planeIndex: Int,
    planes: List<ConstellationPlane>,
    tolerance: Double,
): PlaneDiagram {
    val plane = planes[planeIndex]
    val points = arrayListOf<Vec3>()
    fun pointIndex(point: Vec3): Int {
        val existing = points.indexOfFirst { candidate -> (candidate - point).norm <= tolerance * 8.0 }
        if (existing >= 0) return existing
        points += point
        return points.lastIndex
    }

    val edges = linkedSetOf<DiagramEdge>()
    for (otherIndex in planes.indices) {
        if (otherIndex == planeIndex) continue
        val other = planes[otherIndex]
        val direction = plane.normal cross other.normal
        if (direction.norm <= CONSTELLATION_EPS) continue
        val linePoints = arrayListOf<Vec3>()
        for (thirdIndex in planes.indices) {
            if (thirdIndex == planeIndex || thirdIndex == otherIndex) continue
            val third = planes[thirdIndex]
            val determinant = plane.normal * (other.normal cross third.normal)
            if (abs(determinant) <= CONSTELLATION_EPS) continue
            val point = planeIntersection(plane, other, third)
            if (point.isFinite()) linePoints.addDistinct(point, tolerance * 8.0)
        }
        val ordered = linePoints.sortedBy { point -> point * direction }
        for (index in 0 until ordered.lastIndex) {
            val first = pointIndex(ordered[index])
            val second = pointIndex(ordered[index + 1])
            if (first == second) continue
            edges += if (first < second) DiagramEdge(first, second) else DiagramEdge(second, first)
        }
    }

    val neighbors = List(points.size) { arrayListOf<Int>() }
    for (edge in edges) {
        neighbors[edge.a] += edge.b
        neighbors[edge.b] += edge.a
    }
    for (node in neighbors.indices) {
        neighbors[node].sortBy { target ->
            val direction = points[target] - points[node]
            atan2(direction * plane.v, direction * plane.u)
        }
    }

    val visited = hashSetOf<Pair<Int, Int>>()
    val facets = arrayListOf<DiagramFacet>()
    for (edge in edges) for ((start, next) in listOf(edge.a to edge.b, edge.b to edge.a)) {
        if (!visited.add(start to next)) continue
        val boundary = arrayListOf(start)
        var previous = start
        var current = next
        while (current != start) {
            boundary += current
            val outgoing = neighbors[current]
            val reverse = outgoing.indexOf(previous)
            require(reverse >= 0) { "Stellation diagram contains an unlinked half-edge" }
            val following = outgoing[(reverse + outgoing.size - 1) % outgoing.size]
            previous = current
            current = following
            require(visited.add(previous to current)) { "Stellation diagram contains a non-closing walk" }
            require(boundary.size <= edges.size * 2) { "Stellation diagram walk exceeds its edge count" }
        }
        val polygon = boundary.map(points::get)
        val signedArea = polygon.indices.sumOf { index ->
            ((polygon[index] - plane.center) cross
                (polygon[(index + 1) % polygon.size] - plane.center)) * plane.normal
        } / 2.0
        if (signedArea <= tolerance * tolerance) continue
        val sample = polygon.reduce(Vec3::plus) * (1.0 / polygon.size)
        require(planes.indices.none { index ->
            index != planeIndex && abs(planes[index].normal * sample - planes[index].distance) <= tolerance
        }) { "Stellation facet sample lies on another source plane" }
        val outside = planes.indices.filter { index ->
            index != planeIndex && planes[index].normal * sample > planes[index].distance + tolerance
        }
        facets += DiagramFacet(
            polygon,
            ArrangementCell(outside),
            ArrangementCell((outside + planeIndex).sorted()),
        )
    }
    require(facets.isNotEmpty()) { "Face ${plane.face.id} has no bounded stellation facets" }
    return PlaneDiagram(plane, facets)
}

private suspend fun buildMainLineCandidate(
    diagrams: List<PlaneDiagram>,
    power: Int,
    tolerance: Double,
): Polyhedron {
    val positions = arrayListOf<Vec3>()
    fun vertexIndex(point: Vec3): Int {
        val existing = positions.indexOfFirst { candidate -> (candidate - point).norm <= tolerance * 8.0 }
        if (existing >= 0) return existing
        positions += point
        return positions.lastIndex
    }

    val faces = diagrams.flatMap { diagram ->
        val facets = diagram.facets.filter { facet -> facet.power == power }
        require(facets.isNotEmpty()) { "Main-line stratum $power is absent from face ${diagram.plane.face.id}" }
        reconstructFaceCircuits(facets, diagram.plane, tolerance).map { circuit ->
            circuit.map(::vertexIndex) to diagram.plane.face.kind
        }
    }

    val edgeUses = linkedMapOf<DiagramEdge, Int>()
    for ((face, _) in faces) for (index in face.indices) {
        val first = face[index]
        val second = face[(index + 1) % face.size]
        require(first != second) { "Main-line stratum $power contains a collapsed edge" }
        val edge = if (first < second) DiagramEdge(first, second) else DiagramEdge(second, first)
        edgeUses[edge] = edgeUses.getOrElse(edge) { 0 } + 1
    }
    require(edgeUses.values.all { uses -> uses == 2 }) {
        "Main-line stratum $power is not a closed two-manifold"
    }

    val result = polyhedron(mergeIndistinguishableKinds = true) {
        positions.forEachIndexed { index, point -> vertex(point, VertexKind(index)) }
        faces.forEachIndexed { index, (face, sourceKind) ->
            val kind = FaceKind(index)
            face(face, kind)
            faceKindSource(kind, sourceKind)
        }
    }
    val components = result.surfaceComponentCount()
    if (components != 1) throw CompoundStellationException(components)
    result.validateRenderableImmersion()
    result.resolved(null).validateProperGeometry()
    return result
}

internal fun Polyhedron.surfaceComponentCount(): Int {
    val visited = hashSetOf<Face>()
    var components = 0
    for (first in fs) {
        if (first in visited) continue
        components++
        val pending = ArrayDeque<Face>()
        pending += first
        while (pending.isNotEmpty()) {
            val face = pending.removeFirst()
            if (!visited.add(face)) continue
            face.directedEdges.mapTo(pending, Edge::l)
        }
    }
    return components
}

private fun reconstructFaceCircuits(
    facets: List<DiagramFacet>,
    plane: ConstellationPlane,
    tolerance: Double,
): List<List<Vec3>> {
    val points = arrayListOf<Vec3>()
    fun pointIndex(point: Vec3): Int {
        val existing = points.indexOfFirst { candidate -> (candidate - point).norm <= tolerance * 8.0 }
        if (existing >= 0) return existing
        points += point
        return points.lastIndex
    }
    val edgeUses = linkedMapOf<DiagramEdge, Int>()
    for (facet in facets) {
        val ids = facet.points.map(::pointIndex)
        for (index in ids.indices) {
            val first = ids[index]
            val second = ids[(index + 1) % ids.size]
            val edge = if (first < second) DiagramEdge(first, second) else DiagramEdge(second, first)
            edgeUses[edge] = edgeUses.getOrElse(edge) { 0 } + 1
        }
    }
    val boundary = edgeUses.filterValues { uses -> uses == 1 }.keys
    require(boundary.isNotEmpty()) { "Main-line face ${plane.face.id} has no boundary" }
    val neighbors = List(points.size) { arrayListOf<Int>() }
    for (edge in boundary) {
        neighbors[edge.a] += edge.b
        neighbors[edge.b] += edge.a
    }
    require(neighbors.filter(List<Int>::isNotEmpty).all { adjacent -> adjacent.size % 2 == 0 }) {
        "Main-line face ${plane.face.id} has an open diagram boundary"
    }

    val remaining = boundary.toMutableSet()
    val circuits = arrayListOf<List<Int>>()
    while (remaining.isNotEmpty()) {
        val firstEdge = remaining.minWith(compareBy(DiagramEdge::a, DiagramEdge::b))
        val circuit = arrayListOf(firstEdge.a)
        var previous = firstEdge.a
        var current = firstEdge.b
        remaining.remove(firstEdge)
        while (current != circuit.first()) {
            circuit += current
            val incoming = (points[current] - points[previous]).unit
            val candidates = neighbors[current].filter { next ->
                val edge = if (current < next) DiagramEdge(current, next) else DiagramEdge(next, current)
                edge in remaining
            }
            require(candidates.isNotEmpty()) { "Main-line face ${plane.face.id} contains a non-closing circuit" }
            val next = candidates.maxWith(compareBy<Int> { candidate ->
                incoming * (points[candidate] - points[current]).unit
            }.thenByDescending { candidate -> candidate })
            val edge = if (current < next) DiagramEdge(current, next) else DiagramEdge(next, current)
            remaining.remove(edge)
            previous = current
            current = next
            require(circuit.size <= boundary.size) { "Main-line face ${plane.face.id} circuit exceeds its edge count" }
        }
        circuits += circuit.removeInlineVertices(points, tolerance)
    }
    return circuits.map { circuit ->
        val result = circuit.map(points::get)
        if (result.averagePlane() * plane.normal >= 0.0) result else result.asReversed()
    }
}

private fun buildMainLinePhysicalBoundary(
    diagrams: List<PlaneDiagram>,
    power: Int,
    tolerance: Double,
): Polyhedron {
    val positions = arrayListOf<Vec3>()
    fun vertexIndex(point: Vec3): Int {
        val existing = positions.indexOfFirst { candidate -> (candidate - point).norm <= tolerance * 8.0 }
        if (existing >= 0) return existing
        positions += point
        return positions.lastIndex
    }
    val faces = diagrams.flatMap { diagram ->
        diagram.facets.filter { facet -> facet.power == power }.map { facet ->
            val oriented = if (facet.points.averagePlane() * diagram.plane.normal >= 0.0) {
                facet.points
            } else {
                facet.points.asReversed()
            }
            oriented.map(::vertexIndex) to diagram.plane.face.kind
        }
    }
    val result = polyhedron(mergeIndistinguishableKinds = true) {
        positions.forEachIndexed { index, point -> vertex(point, VertexKind(index)) }
        faces.forEachIndexed { index, (face, sourceKind) ->
            val kind = FaceKind(index)
            face(face, kind)
            faceKindSource(kind, sourceKind)
        }
    }
    val components = result.surfaceComponentCount()
    if (components != 1) throw CompoundStellationException(components)
    result.validateProperGeometry()
    return result
}

private fun List<Int>.removeInlineVertices(points: List<Vec3>, tolerance: Double): List<Int> =
    filterIndexed { index, current ->
        val previous = this[(index + lastIndex) % size]
        val next = this[(index + 1) % size]
        val incoming = points[current] - points[previous]
        val outgoing = points[next] - points[current]
        (incoming cross outgoing).norm > tolerance * maxOf(incoming.norm, outgoing.norm)
    }.also { result -> require(result.size >= 3) { "Main-line face collapsed below three vertices" } }

private fun Face.toConstellationPlane(tolerance: Double): ConstellationPlane {
    var normal: Vec3 = this.unit
    var distance = d
    if (distance < 0.0) {
        normal = normal * -1.0
        distance = -distance
    }
    require(normal.norm > tolerance && distance.isFinite()) { "Face $id has no finite supporting plane" }
    val center = normal * distance
    val axis = if (abs(normal.x) < 0.8) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
    val u = (axis cross normal).unit
    val v = (normal cross u).unit
    val sourceRadius = fvs.sumOf { point -> (point - center).norm } / fvs.size
    return ConstellationPlane(this, normal, distance, center, u, v, sourceRadius)
}

private fun ConstellationPlane.circuits(
    points: List<Vec3>,
    tolerance: Double,
): Map<CandidateKey, FaceCircuit> {
    // Match symmetry-equivalent rings by their dimensionless radius. Quantizing an absolute
    // radius against a global tolerance lets platform-specific last bits put equivalent faces in
    // adjacent bins (notably in Wasm), leaving the cross-face key intersection empty.
    val relativeRadiusBinSize = CONSTELLATION_EPS * 64.0
    val rings = linkedMapOf<RingKey, MutableList<Vec3>>()
    for (point in points) {
        val radius = (point - center).norm
        if (radius <= tolerance) continue
        val key = RingKey(
            size = 0,
            radiusBin = (radius / sourceRadius / relativeRadiusBinSize).roundToLong(),
        )
        rings.getOrPut(key, ::arrayListOf).addDistinct(point, tolerance)
    }
    val result = linkedMapOf<CandidateKey, FaceCircuit>()
    for ((rawKey, ringPoints) in rings) {
        if (ringPoints.size < 3) continue
        val ordered = ringPoints.sortedBy { point -> atan2((point - center) * v, (point - center) * u) }
        if (!ordered.isRegularRing(center, tolerance * 32.0)) continue
        val radius = ordered.sumOf { point -> (point - center).norm } / ordered.size
        val ringKey = rawKey.copy(size = ordered.size)
        for (step in 1 until (ordered.size + 1) / 2) {
            if (greatestCommonDivisor(ordered.size, step) != 1) continue
            val raw = List(ordered.size) { index -> ordered[(index * step) % ordered.size] }
            val pointsOriented = if (raw.averagePlane().let { plane -> plane * normal } >= 0.0) raw else raw.asReversed()
            result[CandidateKey(ringKey, step)] = FaceCircuit(pointsOriented, step, radius)
        }
    }
    return result
}

private suspend fun buildCandidate(
    planes: List<ConstellationPlane>,
    circuits: List<FaceCircuit>,
    tolerance: Double,
): Polyhedron {
    val positions = arrayListOf<Vec3>()
    fun vertexIndex(point: Vec3): Int {
        val existing = positions.indexOfFirst { candidate -> (candidate - point).norm <= tolerance * 8.0 }
        if (existing >= 0) return existing
        positions += point
        return positions.lastIndex
    }
    val faceIndices = circuits.map { circuit -> circuit.points.map(::vertexIndex) }
    val edgeUses = linkedMapOf<Pair<Int, Int>, Int>()
    for (face in faceIndices) for (index in face.indices) {
        val a = face[index]
        val b = face[(index + 1) % face.size]
        require(a != b) { "Constellation candidate contains a collapsed edge" }
        val edge = if (a < b) a to b else b to a
        edgeUses[edge] = edgeUses.getOrElse(edge) { 0 } + 1
    }
    require(edgeUses.values.all { uses -> uses == 2 }) {
        "Constellation candidate is not a closed two-manifold"
    }
    val result = polyhedron(mergeIndistinguishableKinds = true) {
        positions.forEachIndexed { index, point -> vertex(point, VertexKind(index)) }
        faceIndices.forEachIndexed { index, face ->
            val kind = FaceKind(index)
            face(face, kind)
            faceKindSource(kind, planes[index].face.kind)
        }
    }
    val components = result.surfaceComponentCount()
    if (components != 1) throw CompoundStellationException(components)
    result.validateRenderableImmersion()
    result.resolved(null).validateProperGeometry()
    return result
}

internal fun Polyhedron.sameGeometryAs(other: Polyhedron, tolerance: Double): Boolean =
    fev() == other.fev() &&
        coordinateSignature(tolerance) == other.coordinateSignature(tolerance) &&
        edgeSignature(tolerance) == other.edgeSignature(tolerance)

internal fun Polyhedron.coordinateSignature(tolerance: Double): List<Triple<Long, Long, Long>> = vs.map { point ->
    Triple(
        (point.x / tolerance).roundToLong(),
        (point.y / tolerance).roundToLong(),
        (point.z / tolerance).roundToLong(),
    )
}.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))

internal fun Polyhedron.edgeSignature(tolerance: Double): List<Pair<Triple<Long, Long, Long>, Triple<Long, Long, Long>>> {
    val points = vs.map { point ->
        Triple(
            (point.x / tolerance).roundToLong(),
            (point.y / tolerance).roundToLong(),
            (point.z / tolerance).roundToLong(),
        )
    }
    return fs.flatMap { face -> face.fvs.indices.map { index ->
        val first = points[face.fvs[index].id]
        val second = points[face.fvs[(index + 1) % face.fvs.size].id]
        if (comparePoints(first, second) <= 0) first to second else second to first
    } }.distinct().sortedWith(compareBy({ it.first.first }, { it.first.second }, { it.first.third },
        { it.second.first }, { it.second.second }, { it.second.third }))
}

private fun Polyhedron.normalizedConstellationSignature(): List<NormalizedFaceSignature> {
    val inverseRadius = 1.0 / circumradius
    val quantization = 1e-8
    fun coordinate(point: Vec3) = Triple(
        (point.x * inverseRadius / quantization).roundToLong(),
        (point.y * inverseRadius / quantization).roundToLong(),
        (point.z * inverseRadius / quantization).roundToLong(),
    )
    return fs.map { face ->
        var normal = face.unit
        var distance = face.d
        if (distance < 0.0) {
            normal = normal * -1.0
            distance = -distance
        }
        NormalizedFaceSignature(
            face.kind.id,
            listOf(
                (normal.x / quantization).roundToLong(),
                (normal.y / quantization).roundToLong(),
                (normal.z / quantization).roundToLong(),
                (distance * inverseRadius / quantization).roundToLong(),
            ),
            face.fvs.map(::coordinate),
        )
    }
}

internal fun Polyhedron.meanFaceCircuitRadius(): Double = fs.sumOf { face ->
    val center = face.unit * face.d
    face.fvs.sumOf { point -> (point - center).norm } / face.fvs.size
} / fs.size

private fun MutableList<Vec3>.addDistinct(point: Vec3, tolerance: Double) {
    if (none { candidate -> (candidate - point).norm <= tolerance }) add(point)
}

private fun List<Vec3>.isRegularRing(center: Vec3, tolerance: Double): Boolean {
    val radii = map { point -> (point - center).norm }
    val mean = radii.average()
    return radii.all { radius -> abs(radius - mean) <= tolerance * maxOf(1.0, mean) }
}

private fun comparePoints(
    first: Triple<Long, Long, Long>,
    second: Triple<Long, Long, Long>,
): Int = when {
    first.first != second.first -> first.first.compareTo(second.first)
    first.second != second.second -> first.second.compareTo(second.second)
    else -> first.third.compareTo(second.third)
}

private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private fun ConstellationPlane.coincidesWith(other: ConstellationPlane, tolerance: Double): Boolean =
    (normal - other.normal).norm <= tolerance && abs(distance - other.distance) <= tolerance

private fun greatestCommonDivisor(first: Int, second: Int): Int {
    var a = first
    var b = second
    while (b != 0) {
        val remainder = a % b
        a = b
        b = remainder
    }
    return abs(a)
}
