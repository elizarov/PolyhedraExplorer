package polyhedra.core.transform

import polyhedra.core.poly.geometricSymmetryOperations
import polyhedra.core.poly.polyhedron
import polyhedra.core.poly.scaled
import polyhedra.core.poly.validateProperGeometry
import polyhedra.core.poly.validateRenderableImmersion
import polyhedra.core.util.OperationProgressContext
import polyhedra.core.util.reportProgress
import polyhedra.core.util.subrange
import polyhedra.model.api.MAX_POLYHEDRON_EDGES
import polyhedra.model.poly.Face
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.size
import polyhedra.model.util.Vec3
import polyhedra.model.util.averagePlane
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import polyhedra.model.util.unit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToLong

private const val MAX_FACETING_TRIPLES = 2_000_000L
private const val MAX_FACETING_ORBITS = 512
private const val MAX_FACETING_ORBITS_PER_SURFACE = 8
private const val MAX_FACETING_SEARCH_NODES = 10_000

private data class FacetingPlaneKey(
    val x: Long,
    val y: Long,
    val z: Long,
    val d: Long,
)

private data class FacetingPlane(
    val normal: Vec3,
    val distance: Double,
    val vertexIds: List<Int>,
)

private data class FacetingFace(val vertexIds: List<Int>) {
    val key: String = vertexIds.joinToString(",")
}

private data class FacetingFaceOrbit(
    val faces: List<FacetingFace>,
    val edgeUses: Map<Long, Int>,
    val vertexIds: Set<Int>,
    val key: String,
)

/**
 * Enumerates symmetric facetings of the polar dual without moving or dropping any dual vertex,
 * then reciprocates them. Keeping every dual vertex preserves every authoritative source-face
 * plane. This is the sole Greatened construction; catalog recognition is only applied afterward.
 */
internal suspend fun Polyhedron.buildGenericGreateningCandidates(
    tolerance: Double,
    progress: OperationProgressContext?,
): List<StellationCandidate> {
    val dual = runCatching { directDual() }.getOrNull() ?: return emptyList()
    if (dual.vs.size < 4) return emptyList()
    val symmetries = runCatching { dual.geometricSymmetryOperations() }.getOrNull() ?: return emptyList()
    progress?.reportProgress(5)
    val vertexRepresentatives = dual.vertexOrbitRepresentatives(symmetries.all.map { it.vertexPermutation })
    val estimatedTriples = vertexRepresentatives.size.toLong() *
        (dual.vs.size - 1L) * (dual.vs.size - 2L) / 2L
    if (estimatedTriples > MAX_FACETING_TRIPLES) return emptyList()

    val planeTolerance = maxOf(tolerance / circumradius.coerceAtLeast(1.0), 2e-7)
    val planes = dual.facetingPlanes(vertexRepresentatives, planeTolerance, progress?.subrange(5, 10))
    if (planes.isEmpty()) return emptyList()
    val orbits = dual.facetingFaceOrbits(planes, symmetries.all, planeTolerance, progress?.subrange(10, 17))
        .pruneUnmatchableOrbits()
        .take(MAX_FACETING_ORBITS)
    if (orbits.isEmpty()) return emptyList()

    val facetedDuals = dual.assembleFacetings(orbits, progress?.subrange(17, 25))
    val candidates = facetedDuals.mapIndexedNotNull { index, faceted ->
        val candidateProgress = progress?.subrange(
            25 + 73 * index / facetedDuals.size.coerceAtLeast(1),
            25 + 73 * (index + 1) / facetedDuals.size.coerceAtLeast(1),
        )
        val candidate = runCatching {
            faceted.validateRenderableImmersion()
            val candidate = alignGreateningToSourcePlanes(faceted.directDual(), planeTolerance)
                ?: return@runCatching null
            candidate.validateRenderableImmersion()
            candidate.resolved(candidateProgress).validateProperGeometry()
            candidate
        }.getOrNull()
        candidateProgress?.reportProgress(100)
        candidate
    }.distinctBy { candidate ->
        candidate.coordinateSignature(tolerance) to candidate.edgeSignature(tolerance)
    }

    val result = candidates
        .sortedWith(compareBy<Polyhedron>(
            { candidate -> candidate.facePatternMismatchCount(this) },
            { candidate -> candidate.totalFaceArityDelta(this) },
            { candidate -> candidate.totalFaceWindingDelta(this) },
            Polyhedron::meanFaceCircuitRadius,
            { candidate -> candidate.fs.size },
            { candidate -> candidate.es.size },
            { candidate -> candidate.vs.size },
        ))
        .map(::StellationCandidate)
    progress?.reportProgress(100)
    return result
}

private fun Polyhedron.facePatternMismatchCount(source: Polyhedron): Int =
    fs.indices.count { index ->
        fs[index].size != source.fs[index].size || fs[index].circuitStep() != source.fs[index].circuitStep()
    }

private fun Polyhedron.totalFaceArityDelta(source: Polyhedron): Int =
    fs.indices.sumOf { index -> abs(fs[index].size - source.fs[index].size) }

private fun Polyhedron.totalFaceWindingDelta(source: Polyhedron): Int =
    fs.indices.sumOf { index -> abs(fs[index].circuitStep() - source.fs[index].circuitStep()) }

private fun Polyhedron.vertexOrbitRepresentatives(permutations: List<IntArray>): List<Int> {
    val unseen = vs.indices.toMutableSet()
    val result = arrayListOf<Int>()
    while (unseen.isNotEmpty()) {
        val first = unseen.first()
        val orbit = permutations.mapTo(linkedSetOf()) { permutation -> permutation[first] }
        result += orbit.min()
        unseen.removeAll(orbit)
    }
    return result
}

private fun Polyhedron.facetingPlanes(
    representatives: List<Int>,
    tolerance: Double,
    progress: OperationProgressContext?,
): List<FacetingPlane> {
    val tripleKeys = hashSetOf<Long>()
    val planes = linkedMapOf<FacetingPlaneKey, Pair<Vec3, Double>>()
    val count = vs.size
    for ((representativeIndex, representative) in representatives.withIndex()) {
        for (second in vs.indices) {
            if (second == representative) continue
            for (third in second + 1 until count) {
                if (third == representative) continue
                val ids = listOf(representative, second, third).sorted()
                val tripleKey = (ids[0].toLong() * count + ids[1]) * count + ids[2]
                if (!tripleKeys.add(tripleKey)) continue
                val firstPoint = vs[ids[0]]
                val rawNormal = (vs[ids[1]] - firstPoint) cross (vs[ids[2]] - firstPoint)
                if (rawNormal.norm <= tolerance) continue
                var normal = rawNormal.unit
                var distance = normal * firstPoint
                if (distance < 0.0) {
                    normal *= -1.0
                    distance = -distance
                }
                if (distance <= tolerance) continue
                val quantum = tolerance * 8.0
                val key = FacetingPlaneKey(
                    (normal.x / quantum).roundToLong(),
                    (normal.y / quantum).roundToLong(),
                    (normal.z / quantum).roundToLong(),
                    (distance / quantum).roundToLong(),
                )
                planes.getOrPut(key) { normal to distance }
            }
        }
        progress?.reportProgress(representativeIndex + 1, representatives.size)
    }
    return planes.values.mapNotNull { (normal, distance) ->
        val vertexIds = vs.indices.filter { vertexId ->
            abs(normal * vs[vertexId] - distance) <= tolerance * 16.0
        }
        vertexIds.takeIf { it.size >= 3 }?.let { FacetingPlane(normal, distance, it) }
    }
}

private fun Polyhedron.facetingFaceOrbits(
    planes: List<FacetingPlane>,
    symmetries: List<polyhedra.core.poly.GeometricSymmetryOperation>,
    tolerance: Double,
    progress: OperationProgressContext?,
): List<FacetingFaceOrbit> {
    val result = linkedMapOf<String, FacetingFaceOrbit>()
    val visitedPlaneOrbits = hashSetOf<String>()
    for ((planeIndex, plane) in planes.withIndex()) {
        val planeOrbitKey = symmetries.minOf { operation ->
            plane.vertexIds.map { vertexId -> operation.vertexPermutation[vertexId] }
                .sorted()
                .joinToString(",")
        }
        if (visitedPlaneOrbits.add(planeOrbitKey)) {
            for (circuit in plane.circuits(this, tolerance)) {
                val faces = symmetries.map { operation ->
                    val mapped = circuit.map { vertexId -> operation.vertexPermutation[vertexId] }
                    FacetingFace(canonicalCycle(if (operation.orientation > 0) mapped else mapped.asReversed()))
                }.distinctBy(FacetingFace::key).sortedBy(FacetingFace::key)
                val edgeUses = linkedMapOf<Long, Int>()
                val vertexIds = linkedSetOf<Int>()
                for (face in faces) {
                    vertexIds += face.vertexIds
                    for (index in face.vertexIds.indices) {
                        val edge = edgeKey(
                            face.vertexIds[index],
                            face.vertexIds[(index + 1) % face.vertexIds.size],
                            vs.size,
                        )
                        edgeUses[edge] = edgeUses.getOrElse(edge) { 0 } + 1
                    }
                }
                if (edgeUses.values.any { uses -> uses > 2 }) continue
                val key = faces.joinToString("|") { face -> face.key }
                result.getOrPut(key) { FacetingFaceOrbit(faces, edgeUses, vertexIds, key) }
            }
        }
        progress?.reportProgress(planeIndex + 1, planes.size)
    }
    return result.values.sortedWith(compareBy(
        { orbit -> orbit.faces.size },
        { orbit -> orbit.faces.sumOf { face -> face.vertexIds.size } },
        FacetingFaceOrbit::key,
    ))
}

private fun FacetingPlane.circuits(poly: Polyhedron, tolerance: Double): List<List<Int>> {
    val center = normal * distance
    val axis = if (abs(normal.x) < 0.8) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
    val u = (axis cross normal).unit
    val v = (normal cross u).unit
    val pointSets = linkedMapOf<String, List<Int>>()

    fun addPointSet(ids: List<Int>) {
        if (ids.size < 3) return
        val ordered = ids.distinct().sortedBy { vertexId ->
            val point = poly.vs[vertexId]
            atan2((point - center) * v, (point - center) * u)
        }
        pointSets.getOrPut(ordered.sorted().joinToString(",")) { ordered }
    }

    addPointSet(convexHull(vertexIds, poly, u, v))
    val radiusQuantum = tolerance * 32.0
    vertexIds.groupBy { vertexId ->
        ((poly.vs[vertexId] - center).norm / radiusQuantum).roundToLong()
    }.values.forEach(::addPointSet)

    val result = linkedMapOf<String, List<Int>>()
    for (ordered in pointSets.values) {
        for (step in 1 until (ordered.size + 1) / 2) {
            if (greatestCommonDivisor(ordered.size, step) != 1) continue
            val raw = List(ordered.size) { index -> ordered[(index * step) % ordered.size] }
            val oriented = if (raw.map(poly.vs::get).averagePlane() * normal >= 0.0) raw else raw.asReversed()
            val canonical = canonicalCycle(oriented)
            result.getOrPut(canonical.joinToString(",")) { canonical }
        }
    }
    return result.values.toList()
}

private data class ProjectedVertex(val id: Int, val x: Double, val y: Double)

private fun convexHull(
    vertexIds: List<Int>,
    poly: Polyhedron,
    u: Vec3,
    v: Vec3,
): List<Int> {
    val points = vertexIds.map { id -> ProjectedVertex(id, poly.vs[id] * u, poly.vs[id] * v) }
        .sortedWith(compareBy(ProjectedVertex::x, ProjectedVertex::y, ProjectedVertex::id))
    if (points.size <= 3) return points.map(ProjectedVertex::id)
    fun turn(a: ProjectedVertex, b: ProjectedVertex, c: ProjectedVertex): Double =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    val lower = arrayListOf<ProjectedVertex>()
    for (point in points) {
        while (lower.size >= 2 && turn(lower[lower.lastIndex - 1], lower.last(), point) <= 0.0) {
            lower.removeAt(lower.lastIndex)
        }
        lower += point
    }
    val upper = arrayListOf<ProjectedVertex>()
    for (point in points.asReversed()) {
        while (upper.size >= 2 && turn(upper[upper.lastIndex - 1], upper.last(), point) <= 0.0) {
            upper.removeAt(upper.lastIndex)
        }
        upper += point
    }
    return (lower.dropLast(1) + upper.dropLast(1)).map(ProjectedVertex::id)
}

private fun Polyhedron.assembleFacetings(
    orbits: List<FacetingFaceOrbit>,
    progress: OperationProgressContext?,
): List<Polyhedron> {
    val result = arrayListOf<Polyhedron>()
    val resultKeys = hashSetOf<String>()
    val visitedSelections = hashSetOf<String>()
    val openEdgeToOrbits = linkedMapOf<Long, MutableList<Int>>()
    orbits.forEachIndexed { orbitId, orbit ->
        orbit.edgeUses.filterValues { uses -> uses == 1 }.keys.forEach { edge ->
            openEdgeToOrbits.getOrPut(edge, ::arrayListOf) += orbitId
        }
    }
    var searchNodes = 0
    var lastReportedProgress = -1

    fun build(chosen: List<Int>) {
        val key = chosen.sorted().joinToString(",")
        if (!resultKeys.add(key)) return
        val faceCount = chosen.sumOf { orbitId -> orbits[orbitId].faces.size }
        val edgeCount = chosen.sumOf { orbitId -> orbits[orbitId].edgeUses.count { it.value == 1 } } / 2 +
            chosen.sumOf { orbitId -> orbits[orbitId].edgeUses.count { it.value == 2 } }
        if (edgeCount > MAX_POLYHEDRON_EDGES || faceCount < 4) return
        runCatching {
            polyhedron(mergeIndistinguishableKinds = true) {
                this@assembleFacetings.vs.forEach { vertex -> vertex(vertex, vertex.kind) }
                chosen.forEachIndexed { kindId, orbitId ->
                    orbits[orbitId].faces.forEach { face -> face(face.vertexIds, FaceKind(kindId)) }
                }
            }
        }.getOrNull()?.takeIf { candidate -> candidate.surfaceComponentCount() == 1 }?.let(result::add)
    }

    fun search(
        seed: Int,
        chosen: MutableList<Int>,
        selected: BooleanArray,
        edgeUses: MutableMap<Long, Int>,
        usedVertices: BooleanArray,
    ) {
        if (++searchNodes > MAX_FACETING_SEARCH_NODES) return
        val done = searchNodes * 100 / MAX_FACETING_SEARCH_NODES
        if (done > lastReportedProgress) {
            lastReportedProgress = done
            progress?.reportProgress(done)
        }
        val selectionKey = chosen.sorted().joinToString(",")
        if (!visitedSelections.add(selectionKey)) return
        val openEdges = edgeUses.filterValues { uses -> uses == 1 }.keys
        if (openEdges.isEmpty()) {
            if (usedVertices.all { it }) build(chosen)
            return
        }
        if (chosen.size >= MAX_FACETING_ORBITS_PER_SURFACE) return
        val next = openEdges.minBy { edge ->
            openEdgeToOrbits[edge].orEmpty().count { orbitId -> orbitId >= seed && !selected[orbitId] }
        }
        for (orbitId in openEdgeToOrbits[next].orEmpty()) {
            if (orbitId < seed || selected[orbitId]) continue
            val orbit = orbits[orbitId]
            if (orbit.edgeUses.any { (edge, uses) -> edgeUses.getOrElse(edge) { 0 } + uses > 2 }) continue
            val previousCounts = orbit.edgeUses.mapValues { (edge) -> edgeUses[edge] }
            val previousVertices = orbit.vertexIds.filter { vertexId -> !usedVertices[vertexId] }
            orbit.edgeUses.forEach { (edge, uses) -> edgeUses[edge] = edgeUses.getOrElse(edge) { 0 } + uses }
            previousVertices.forEach { vertexId -> usedVertices[vertexId] = true }
            selected[orbitId] = true
            chosen += orbitId
            search(seed, chosen, selected, edgeUses, usedVertices)
            chosen.removeAt(chosen.lastIndex)
            selected[orbitId] = false
            previousVertices.forEach { vertexId -> usedVertices[vertexId] = false }
            previousCounts.forEach { (edge, previous) ->
                if (previous == null) edgeUses.remove(edge) else edgeUses[edge] = previous
            }
        }
    }

    for (seed in orbits.indices) {
        if (searchNodes > MAX_FACETING_SEARCH_NODES) break
        val orbit = orbits[seed]
        val selected = BooleanArray(orbits.size)
        selected[seed] = true
        val usedVertices = BooleanArray(vs.size)
        orbit.vertexIds.forEach { vertexId -> usedVertices[vertexId] = true }
        search(
            seed,
            mutableListOf(seed),
            selected,
            orbit.edgeUses.toMutableMap(),
            usedVertices,
        )
    }
    progress?.reportProgress(100)
    return result
}

private fun List<FacetingFaceOrbit>.pruneUnmatchableOrbits(): List<FacetingFaceOrbit> {
    var active = indices.toMutableSet()
    do {
        val openEdgeCounts = linkedMapOf<Long, Int>()
        for (orbitId in active) {
            this[orbitId].edgeUses.filterValues { uses -> uses == 1 }.keys.forEach { edge ->
                openEdgeCounts[edge] = openEdgeCounts.getOrElse(edge) { 0 } + 1
            }
        }
        val invalid = active.filterTo(hashSetOf()) { orbitId ->
            this[orbitId].edgeUses.any { (edge, uses) -> uses == 1 && openEdgeCounts[edge].orZero() < 2 }
        }
        active.removeAll(invalid)
    } while (invalid.isNotEmpty())
    return active.map(::get)
}

private fun Int?.orZero(): Int = this ?: 0

private fun Polyhedron.alignGreateningToSourcePlanes(
    candidate: Polyhedron,
    tolerance: Double,
): Polyhedron? {
    if (candidate.fs.size != fs.size) return null
    val scaleFactors = arrayListOf<Double>()
    for (index in fs.indices) {
        val sourceFace = fs[index]
        val candidateFace = candidate.fs[index]
        val sourceNormal = sourceFace.outwardNormal()
        val candidateNormal = candidateFace.outwardNormal()
        if (sourceNormal * candidateNormal < 1.0 - tolerance * 64.0) return null
        val sourceDistance = abs(sourceFace.d)
        val candidateDistance = abs(candidateFace.d)
        if (sourceDistance <= tolerance || candidateDistance <= tolerance) return null
        scaleFactors += sourceDistance / candidateDistance
    }
    val scaleFactor = scaleFactors.average()
    if (scaleFactors.any { factor -> abs(factor - scaleFactor) > tolerance * 64.0 * maxOf(1.0, scaleFactor) }) {
        return null
    }
    val aligned = candidate.scaled(scaleFactor)
    var strictlyLarger = false
    for (index in fs.indices) {
        val sourceRadius = fs[index].circuitRadius()
        val candidateRadius = aligned.fs[index].circuitRadius()
        if (candidateRadius < sourceRadius - tolerance * 64.0) return null
        if (candidateRadius > sourceRadius + tolerance * 64.0) strictlyLarger = true
    }
    return aligned.takeIf { strictlyLarger && !it.sameGeometryAs(this, tolerance * 16.0) }
}

private fun Face.outwardNormal(): Vec3 = if (d >= 0.0) unit else unit * -1.0

private fun Face.circuitRadius(): Double {
    val center = outwardNormal() * abs(d)
    return fvs.sumOf { vertex -> (vertex - center).norm } / fvs.size
}

private fun Face.circuitStep(): Int {
    val normal = outwardNormal()
    val center = normal * abs(d)
    val axis = if (abs(normal.x) < 0.8) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
    val u = (axis cross normal).unit
    val v = (normal cross u).unit
    val angular = fvs.sortedBy { point -> atan2((point - center) * v, (point - center) * u) }
    val firstIndex = angular.indexOfFirst { point -> point.id == fvs[0].id }
    val secondIndex = angular.indexOfFirst { point -> point.id == fvs[1].id }
    if (firstIndex < 0 || secondIndex < 0) return 1
    val raw = (secondIndex - firstIndex + angular.size) % angular.size
    return minOf(raw, angular.size - raw).coerceAtLeast(1)
}

private fun canonicalCycle(cycle: List<Int>): List<Int> {
    val first = cycle.min()
    return cycle.indices
        .filter { index -> cycle[index] == first }
        .map { offset -> List(cycle.size) { index -> cycle[(offset + index) % cycle.size] } }
        .minWith(::compareCycles)
}

private fun compareCycles(first: List<Int>, second: List<Int>): Int {
    for (index in first.indices) {
        val comparison = first[index].compareTo(second[index])
        if (comparison != 0) return comparison
    }
    return 0
}

private fun edgeKey(first: Int, second: Int, vertexCount: Int): Long {
    val a = minOf(first, second)
    val b = maxOf(first, second)
    return a.toLong() * vertexCount + b
}

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
