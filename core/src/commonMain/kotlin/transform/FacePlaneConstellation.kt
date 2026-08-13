package polyhedra.core.transform

import polyhedra.core.poly.polyhedron
import polyhedra.core.poly.scaled
import polyhedra.core.poly.validateRenderableImmersion
import polyhedra.core.poly.validateProperGeometry
import polyhedra.core.util.runSynchronously
import polyhedra.model.poly.FEV
import polyhedra.model.poly.Face
import polyhedra.model.poly.MutableFaceKindSource
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
)

private data class ConstellationPlane(
    val face: Face,
    val normal: Vec3,
    val distance: Double,
    val center: Vec3,
    val u: Vec3,
    val v: Vec3,
    val sourceRadius: Double,
    val sourceStep: Int,
) : Plane {
    override val x: Double get() = normal.x
    override val y: Double get() = normal.y
    override val z: Double get() = normal.z
    override val d: Double get() = distance
}

private data class RingKey(val size: Int, val radiusBin: Long)
private data class FaceCircuit(val points: List<Vec3>, val step: Int, val radius: Double)
private data class CandidateKey(val ring: RingKey, val step: Int)

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
        StellationCandidate(candidate.poly.scaled(Scale.Circumradius))
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

    val continuationCandidates = if (operation == ConstellationOperation.Stellate) {
        edgeContinuationCandidates(planes, tolerance)
    } else {
        emptyList()
    }

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
            if (!qualifies(operation, planes, circuits, tolerance)) return@mapNotNull null
            runCatching { buildCandidate(planes, circuits, tolerance) }
                .getOrNull()
                ?.takeUnless { candidate -> candidate.sameGeometryAs(this, tolerance) }
                ?.let(::StellationCandidate)
        }
        .distinctBy { candidate -> candidate.poly.coordinateSignature(tolerance) to candidate.poly.edgeSignature(tolerance) }

    return (continuationCandidates + constellationCandidates)
        .distinctBy { candidate -> candidate.poly.coordinateSignature(tolerance) to candidate.poly.edgeSignature(tolerance) }
        .sortedWith(compareBy(
        { candidate -> candidate.poly.meanFaceCircuitRadius() },
        { candidate -> candidate.fev.f },
        { candidate -> candidate.fev.e },
        { candidate -> candidate.fev.v },
        ))
}

private suspend fun Polyhedron.edgeContinuationCandidates(
    planes: List<ConstellationPlane>,
    tolerance: Double,
): List<StellationCandidate> {
    val maximumStride = planes.minOf { plane -> (plane.face.fvs.size - 1) / 2 }
    return (2..maximumStride).mapNotNull { stride ->
        if (planes.any { plane -> greatestCommonDivisor(plane.face.fvs.size, stride) != 1 }) {
            return@mapNotNull null
        }
        val circuits = planes.map { plane ->
            val face = plane.face
            val lineOrder = List(face.fvs.size) { index -> (index * stride) % face.fvs.size }
            val points = lineOrder.indices.map { index ->
                val previousLine = lineOrder[(index + lineOrder.size - 1) % lineOrder.size]
                val currentLine = lineOrder[index]
                lineIntersection(
                    face.fvs[previousLine],
                    face.fvs[(previousLine + 1) % face.fvs.size],
                    face.fvs[currentLine],
                    face.fvs[(currentLine + 1) % face.fvs.size],
                    tolerance,
                )
            }
            val oriented = if (points.averagePlane().let { result -> result * plane.normal } >= 0.0) {
                points
            } else {
                points.asReversed()
            }
            FaceCircuit(
                oriented,
                stride,
                oriented.sumOf { point -> (point - plane.center).norm } / oriented.size,
            )
        }
        runCatching { buildCandidate(planes, circuits, tolerance) }
            .getOrNull()
            ?.takeIf { circuits.indices.all { index ->
                circuits[index].radius > planes[index].sourceRadius + tolerance
            } }
            ?.takeUnless { candidate -> candidate.sameGeometryAs(this, tolerance) }
            ?.let(::StellationCandidate)
    }
}

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
    val angular = fvs.sortedBy { point -> atan2((point - center) * v, (point - center) * u) }
    val sourceStep = angular.indices.firstOrNull { angular[it].id == fvs[1].id }
        ?.let { secondIndex ->
            val firstIndex = angular.indexOfFirst { it.id == fvs[0].id }
            val raw = (secondIndex - firstIndex + angular.size) % angular.size
            minOf(raw, angular.size - raw)
        }
        ?.coerceAtLeast(1)
        ?: 1
    val sourceRadius = fvs.sumOf { point -> (point - center).norm } / fvs.size
    return ConstellationPlane(this, normal, distance, center, u, v, sourceRadius, sourceStep)
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

private fun qualifies(
    operation: ConstellationOperation,
    planes: List<ConstellationPlane>,
    circuits: List<FaceCircuit>,
    tolerance: Double,
): Boolean = when (operation) {
    ConstellationOperation.Greaten -> planes.indices.all { index ->
        val plane = planes[index]
        val circuit = circuits[index]
        circuit.points.size == plane.face.fvs.size &&
            circuit.step == plane.sourceStep &&
            circuit.radius > plane.sourceRadius + tolerance
    }
    ConstellationOperation.Stellate -> planes.indices.all { index ->
        val source = planes[index].face.fvs
        val circuit = circuits[index]
        val candidate = circuit.points
        circuit.radius > planes[index].sourceRadius + tolerance && candidate.indices.all { edge ->
            val a = candidate[edge]
            val b = candidate[(edge + 1) % candidate.size]
            source.indices.any { sourceEdge ->
                sameLine(
                    a,
                    b,
                    source[sourceEdge],
                    source[(sourceEdge + 1) % source.size],
                    tolerance * 16.0,
                )
            }
        }
    }
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
        positions.forEach { point -> vertex(point, VertexKind(0)) }
        faceIndices.forEachIndexed { index, face -> face(face, planes[index].face.kind) }
        faceKindSources(
            planes.map { plane -> MutableFaceKindSource(plane.face.kind, plane.face.kind) }
                .distinctBy { source -> source.kind },
        )
    }
    result.validateRenderableImmersion()
    result.resolved(null).validateProperGeometry()
    return result
}

private fun Polyhedron.sameGeometryAs(other: Polyhedron, tolerance: Double): Boolean =
    fev() == other.fev() && coordinateSignature(tolerance) == other.coordinateSignature(tolerance)

private fun Polyhedron.coordinateSignature(tolerance: Double): List<Triple<Long, Long, Long>> = vs.map { point ->
    Triple(
        (point.x / tolerance).roundToLong(),
        (point.y / tolerance).roundToLong(),
        (point.z / tolerance).roundToLong(),
    )
}.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))

private fun Polyhedron.edgeSignature(tolerance: Double): List<Pair<Triple<Long, Long, Long>, Triple<Long, Long, Long>>> {
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

private fun Polyhedron.meanFaceCircuitRadius(): Double = fs.sumOf { face ->
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

private fun sameLine(a: Vec3, b: Vec3, c: Vec3, d: Vec3, tolerance: Double): Boolean {
    val ab = b - a
    val cd = d - c
    if ((ab cross cd).norm > tolerance * ab.norm * cd.norm) return false
    return ((c - a) cross ab).norm <= tolerance * maxOf(1.0, ab.norm)
}

private fun lineIntersection(a: Vec3, b: Vec3, c: Vec3, d: Vec3, tolerance: Double): Vec3 {
    val first = b - a
    val second = d - c
    val normal = first cross second
    val denominator = normal * normal
    require(denominator > tolerance * tolerance * first.norm * second.norm) {
        "Stellation edge lines are parallel"
    }
    val parameter = (((c - a) cross second) * normal) / denominator
    val point = a + first * parameter
    require(((point - c) cross second).norm <= tolerance * maxOf(1.0, second.norm)) {
        "Stellation edge lines are not coplanar"
    }
    return point
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
