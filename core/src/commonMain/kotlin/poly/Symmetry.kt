/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core.poly

import polyhedra.model.api.CoreSymmetry
import polyhedra.model.api.PointGroup
import polyhedra.model.api.PointGroupFamily
import polyhedra.model.api.PointGroupSuffix
import polyhedra.model.poly.Edge
import polyhedra.model.poly.FEV
import polyhedra.model.poly.Face
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.MutableFace
import polyhedra.model.poly.MutableFaceKindSource
import polyhedra.model.poly.MutableVertex
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.Vertex
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.Scale
import polyhedra.model.poly.IsoDir
import polyhedra.model.poly.len
import polyhedra.model.util.MutableVec3
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import polyhedra.model.util.unit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.floor
import kotlin.math.round

private const val SYMMETRY_TOLERANCE = 1e-7
private const val MATRIX_TOLERANCE = 2e-6
private const val MAX_ROTATION_ORDER = 100

/** Finds the full geometric rotation orbits, rotation axes, and mirror planes of this mesh. */
fun Polyhedron.analyzeSymmetry(): CoreSymmetry {
    val operations = geometricSymmetryOperations()
    val proper = operations.proper
    val reversingTransforms = operations.reversing.map(GeometricSymmetryOperation::transform)

    val rotationGroup = classifyRotationGroup(proper.map { it.transform })
    val axisDirections = proper.asSequence()
        .mapNotNull { operation -> operation.transform.rotationAxisOrNull() }
        .distinctDirections()
        .map(Vec3::toMutableDirection)
        .toList()
    val planeNormals = reversingTransforms.asSequence()
        .mapNotNull(OrthogonalTransform::reflectionPlaneNormalOrNull)
        .distinctDirections()
        .map(Vec3::toMutableDirection)
        .toList()
    return CoreSymmetry(
        pointGroup = classifyPointGroup(rotationGroup, reversingTransforms.isNotEmpty(), planeNormals.size),
        orbitCounts = symmetryOrbitCounts(proper),
        reflectionPlaneNormals = planeNormals,
        rotationAxisDirections = axisDirections,
    )
}

internal data class GeometricSymmetryOperation(
    val transform: OrthogonalTransform,
    val vertexPermutation: IntArray,
    val orientation: Int,
)

internal data class GeometricSymmetryOperations(
    val proper: List<GeometricSymmetryOperation>,
    val reversing: List<GeometricSymmetryOperation>,
) {
    val all: List<GeometricSymmetryOperation> get() = proper + reversing
}

internal data class GeometricOrbitDetails(
    val properOperationCount: Int,
    val reversingOperationCount: Int,
    val faceOrbits: List<List<Int>>,
    val edgeOrbits: List<List<Int>>,
    val vertexOrbits: List<List<Int>>,
)

internal fun Polyhedron.geometricOrbitDetails(): GeometricOrbitDetails {
    val operations = geometricSymmetryOperations()
    val orbits = symmetryOrbits(operations.proper)
    return GeometricOrbitDetails(
        properOperationCount = operations.proper.size,
        reversingOperationCount = operations.reversing.size,
        faceOrbits = orbits.faces,
        edgeOrbits = orbits.edges,
        vertexOrbits = orbits.vertices,
    )
}

/** Catalogue equivalence for whole compounds includes the members' relative orientation. */
internal fun Polyhedron.matchesCompoundGeometry(other: Polyhedron): Boolean {
    val source = scaled(Scale.Circumradius)
    val target = other.scaled(Scale.Circumradius)
    val tolerance = SYMMETRY_TOLERANCE * 8.0
    val edge = source.directedEdges.first()
    val frame = edgeFrame(edge, tolerance) ?: return false
    return target.directedEdges.any { candidate ->
        if (!edge.hasMatchingGeometry(candidate, tolerance, reverseSides = false)) return@any false
        val targetFrame = edgeFrame(candidate, tolerance) ?: return@any false
        source.geometricVertexPermutation(target, OrthogonalTransform(frame, targetFrame, 1), 1, tolerance) != null
    }
}

internal fun Polyhedron.withGeometricKinds(): Polyhedron {
    val orbits = geometricOrbitDetails()
    val vertexKinds = IntArray(vs.size)
    val faceKinds = IntArray(fs.size)
    orbits.vertexOrbits.forEachIndexed { kind, ids -> ids.forEach { vertexKinds[it] = kind } }
    orbits.faceOrbits.forEachIndexed { kind, ids -> ids.forEach { faceKinds[it] = kind } }
    val vertices = vs.map { MutableVertex(it.id, it, VertexKind(vertexKinds[it.id])) }
    val faces = fs.map { face ->
        MutableFace(face.id, face.fvs.map { vertices[it.id] }, FaceKind(faceKinds[face.id]))
    }
    val sources = fs.map { face ->
        val oldSource = faceKindSources?.firstOrNull { it.kind == face.kind }?.source ?: face.kind
        MutableFaceKindSource(FaceKind(faceKinds[face.id]), oldSource)
    }.distinct()
    return Polyhedron(vertices, faces, sources, resolvedTopologyProvenance = resolvedTopologyProvenance)
}

internal fun Polyhedron.geometricSymmetryOperations(): GeometricSymmetryOperations {
    val radius = circumradius.coerceAtLeast(1.0)
    val tolerance = radius * SYMMETRY_TOLERANCE
    val vertexIndex = VertexSpatialIndex(vs, tolerance)
    val topology = SymmetryTopology(this)
    val sourceEdge = directedEdges.firstOrNull { edgeFrame(it, tolerance) != null }
        ?: error("Cannot derive a symmetry frame from $this")
    val sourceFrame = requireNotNull(edgeFrame(sourceEdge, tolerance))

    val proper = ArrayList<GeometricSymmetryOperation>()
    var improperSeed: GeometricSymmetryOperation? = null
    for (targetEdge in directedEdges) {
        val targetFrame = edgeFrame(targetEdge, tolerance) ?: continue
        if (sourceEdge.hasMatchingGeometry(targetEdge, tolerance, reverseSides = false)) {
            symmetryOperation(sourceFrame, targetFrame, orientation = 1, vertexIndex, topology)
                ?.let { operation ->
                    if (proper.none { it.transform.approximatelyEquals(operation.transform) }) proper += operation
                }
        }
        if (improperSeed == null && sourceEdge.hasMatchingGeometry(targetEdge, tolerance, reverseSides = true)) {
            improperSeed = symmetryOperation(
                sourceFrame,
                targetFrame,
                orientation = -1,
                vertexIndex,
                topology,
            )
        }
    }
    check(proper.isNotEmpty()) { "Every polyhedron must have the identity symmetry" }
    val reversing = improperSeed?.let { seed ->
        proper.map { operation ->
            GeometricSymmetryOperation(
                transform = operation.transform * seed.transform,
                vertexPermutation = IntArray(vs.size) { vertexId ->
                    operation.vertexPermutation[seed.vertexPermutation[vertexId]]
                },
                orientation = -1,
            )
        }
    }.orEmpty()
    return GeometricSymmetryOperations(proper, reversing)
}

internal data class OrthonormalFrame(
    val radial: Vec3,
    val tangent: Vec3,
    val bitangent: Vec3,
)

private fun edgeFrame(edge: Edge, tolerance: Double): OrthonormalFrame? {
    if (edge.a.norm <= tolerance) return null
    val radial = edge.a.unit
    val tangentComponent = edge.b - radial * (edge.b * radial)
    if (tangentComponent.norm <= tolerance) return null
    val tangent = tangentComponent.unit
    return OrthonormalFrame(radial, tangent, radial cross tangent)
}

private fun Edge.hasMatchingGeometry(target: Edge, tolerance: Double, reverseSides: Boolean): Boolean {
    if (!a.norm.near(target.a.norm, tolerance) || !b.norm.near(target.b.norm, tolerance)) return false
    if (!len.near(target.len, tolerance)) return false
    if (a.directedEdges.size != target.a.directedEdges.size || b.directedEdges.size != target.b.directedEdges.size) {
        return false
    }
    val targetLeft = if (reverseSides) target.r else target.l
    val targetRight = if (reverseSides) target.l else target.r
    return l.fvs.size == targetLeft.fvs.size && r.fvs.size == targetRight.fvs.size &&
        l.d.near(targetLeft.d, tolerance) && r.d.near(targetRight.d, tolerance)
}

private fun Double.near(other: Double, tolerance: Double): Boolean = abs(this - other) <= tolerance

private class SymmetryTopology(poly: Polyhedron) {
    private val edgeIndices = poly.es.withIndex().associate { (index, edge) ->
        edgeKey(edge.a.id, edge.b.id) to index
    }
    private val directedEdges = poly.directedEdges.associateBy { edge -> directedEdgeKey(edge.a.id, edge.b.id) }

    fun edgeIndex(first: Int, second: Int): Int? = edgeIndices[edgeKey(first, second)]

    fun targetFaceId(permutation: IntArray, source: Face, orientation: Int): Int? {
        var targetId = -1
        for (sourceEdge in source.directedEdges) {
            val mappedEdge = directedEdges[
                directedEdgeKey(permutation[sourceEdge.a.id], permutation[sourceEdge.b.id])
            ] ?: return null
            val mappedFace = if (orientation > 0) mappedEdge.r else mappedEdge.l
            if (targetId < 0) {
                targetId = mappedFace.id
            } else if (mappedFace.id != targetId) {
                return null
            }
        }
        return targetId.takeIf { it >= 0 }
    }

    fun isPreserved(permutation: IntArray, poly: Polyhedron, orientation: Int): Boolean =
        poly.es.all { edge -> edgeIndex(permutation[edge.a.id], permutation[edge.b.id]) != null } &&
            poly.fs.all { face -> targetFaceId(permutation, face, orientation) != null }
}

private fun directedEdgeKey(first: Int, second: Int): Long =
    (first.toLong() shl 32) xor (second.toLong() and 0xffffffffL)

private fun Polyhedron.symmetryOperation(
    source: OrthonormalFrame,
    target: OrthonormalFrame,
    orientation: Int,
    vertexIndex: VertexSpatialIndex,
    topology: SymmetryTopology,
): GeometricSymmetryOperation? {
    val transform = OrthogonalTransform(source, target, orientation)
    if (isCompound) {
        val permutation = geometricVertexPermutation(this, transform, orientation, circumradius.coerceAtLeast(1.0) * SYMMETRY_TOLERANCE)
            ?: return null
        return GeometricSymmetryOperation(transform, permutation, orientation)
    }
    val permutation = IntArray(vs.size)
    for (vertex in vs) {
        permutation[vertex.id] = vertexIndex.find(transform(vertex)) ?: return null
    }
    if (!topology.isPreserved(permutation, this, orientation)) return null
    return GeometricSymmetryOperation(transform, permutation, orientation)
}

/** Matches complete oriented component maps, not just possibly coincident vertex positions. */
internal fun Polyhedron.geometricVertexPermutation(
    target: Polyhedron,
    transform: OrthogonalTransform,
    orientation: Int,
    tolerance: Double,
): IntArray? {
    if (vs.size != target.vs.size || es.size != target.es.size || fs.size != target.fs.size ||
        components.size != target.components.size) return null
    val positions = vs.map(transform::invoke)
    val permutation = IntArray(vs.size) { -1 }
    val occupiedComponents = hashSetOf<Int>()
    for (component in components) {
        val start = component.first().directedEdges.first()
        var match: Map<Int, Int>? = null
        for (candidate in target.directedEdges) {
            if (target.vertexComponentIds[candidate.a.id] in occupiedComponents ||
                (positions[start.a.id] - candidate.a).norm > tolerance ||
                (positions[start.b.id] - candidate.b).norm > tolerance) continue
            val mapped = hashMapOf<Int, Int>()
            val inverse = hashMapOf<Int, Int>()
            val darts = hashMapOf<Long, Long>()
            val pending = ArrayDeque<Pair<Edge, Edge>>()
            pending += start to candidate
            var valid = true
            while (pending.isNotEmpty() && valid) {
                val (a, b) = pending.removeFirst()
                val sourceKey = directedEdgeKey(a.a.id, a.b.id)
                val targetKey = directedEdgeKey(b.a.id, b.b.id)
                val previous = darts.put(sourceKey, targetKey)
                if (previous != null) {
                    if (previous != targetKey) valid = false
                    continue
                }
                for ((sv, tv) in listOf(a.a to b.a, a.b to b.b)) {
                    if ((positions[sv.id] - tv).norm > tolerance ||
                        mapped[sv.id]?.let { it != tv.id } == true ||
                        inverse[tv.id]?.let { it != sv.id } == true) { valid = false; break }
                    mapped[sv.id] = tv.id
                    inverse[tv.id] = sv.id
                }
                if (!valid) break
                pending += a.reversed to b.reversed
                pending += a.next(IsoDir.R) to b.next(if (orientation > 0) IsoDir.R else IsoDir.L)
            }
            if (valid) { match = mapped; break }
        }
        val matched = match ?: return null
        matched.forEach { (a, b) -> permutation[a] = b }
        occupiedComponents += target.vertexComponentIds[matched.getValue(start.a.id)]
    }
    return permutation.takeIf { it.all { id -> id >= 0 } }
}

internal class OrthogonalTransform(
    val xx: Double, val xy: Double, val xz: Double,
    val yx: Double, val yy: Double, val yz: Double,
    val zx: Double, val zy: Double, val zz: Double,
) {
    fun approximatelyEquals(other: OrthogonalTransform): Boolean =
        abs(xx - other.xx) < MATRIX_TOLERANCE && abs(xy - other.xy) < MATRIX_TOLERANCE &&
            abs(xz - other.xz) < MATRIX_TOLERANCE && abs(yx - other.yx) < MATRIX_TOLERANCE &&
            abs(yy - other.yy) < MATRIX_TOLERANCE && abs(yz - other.yz) < MATRIX_TOLERANCE &&
            abs(zx - other.zx) < MATRIX_TOLERANCE && abs(zy - other.zy) < MATRIX_TOLERANCE &&
            abs(zz - other.zz) < MATRIX_TOLERANCE
    constructor(source: OrthonormalFrame, target: OrthonormalFrame, orientation: Int) : this(
        xx = target.radial.x * source.radial.x + target.tangent.x * source.tangent.x +
            orientation * target.bitangent.x * source.bitangent.x,
        xy = target.radial.x * source.radial.y + target.tangent.x * source.tangent.y +
            orientation * target.bitangent.x * source.bitangent.y,
        xz = target.radial.x * source.radial.z + target.tangent.x * source.tangent.z +
            orientation * target.bitangent.x * source.bitangent.z,
        yx = target.radial.y * source.radial.x + target.tangent.y * source.tangent.x +
            orientation * target.bitangent.y * source.bitangent.x,
        yy = target.radial.y * source.radial.y + target.tangent.y * source.tangent.y +
            orientation * target.bitangent.y * source.bitangent.y,
        yz = target.radial.y * source.radial.z + target.tangent.y * source.tangent.z +
            orientation * target.bitangent.y * source.bitangent.z,
        zx = target.radial.z * source.radial.x + target.tangent.z * source.tangent.x +
            orientation * target.bitangent.z * source.bitangent.x,
        zy = target.radial.z * source.radial.y + target.tangent.z * source.tangent.y +
            orientation * target.bitangent.z * source.bitangent.y,
        zz = target.radial.z * source.radial.z + target.tangent.z * source.tangent.z +
            orientation * target.bitangent.z * source.bitangent.z,
    )

    operator fun invoke(vector: Vec3): Vec3 = Vec3(
        xx * vector.x + xy * vector.y + xz * vector.z,
        yx * vector.x + yy * vector.y + yz * vector.z,
        zx * vector.x + zy * vector.y + zz * vector.z,
    )

    operator fun times(other: OrthogonalTransform): OrthogonalTransform = OrthogonalTransform(
        xx = xx * other.xx + xy * other.yx + xz * other.zx,
        xy = xx * other.xy + xy * other.yy + xz * other.zy,
        xz = xx * other.xz + xy * other.yz + xz * other.zz,
        yx = yx * other.xx + yy * other.yx + yz * other.zx,
        yy = yx * other.xy + yy * other.yy + yz * other.zy,
        yz = yx * other.xz + yy * other.yz + yz * other.zz,
        zx = zx * other.xx + zy * other.yx + zz * other.zx,
        zy = zx * other.xy + zy * other.yy + zz * other.zy,
        zz = zx * other.xz + zy * other.yz + zz * other.zz,
    )

    val trace: Double get() = xx + yy + zz

    fun rotationAxisOrNull(): Vec3? {
        if (abs(trace - 3.0) <= MATRIX_TOLERANCE) return null
        val rows = listOf(
            Vec3(xx - 1.0, xy, xz),
            Vec3(yx, yy - 1.0, yz),
            Vec3(zx, zy, zz - 1.0),
        )
        val axis = listOf(
            rows[0] cross rows[1],
            rows[0] cross rows[2],
            rows[1] cross rows[2],
        ).maxBy(Vec3::norm)
        if (axis.norm <= MATRIX_TOLERANCE) return null
        return axis.unit.canonicalDirection()
    }

    fun reflectionPlaneNormalOrNull(): Vec3? {
        if (abs(trace - 1.0) > MATRIX_TOLERANCE) return null
        if (
            abs(xy - yx) > MATRIX_TOLERANCE ||
            abs(xz - zx) > MATRIX_TOLERANCE ||
            abs(yz - zy) > MATRIX_TOLERANCE
        ) return null
        val rows = listOf(
            Vec3(1.0 - xx, -xy, -xz),
            Vec3(-yx, 1.0 - yy, -yz),
            Vec3(-zx, -zy, 1.0 - zz),
        )
        val normal = rows.maxBy(Vec3::norm)
        if (normal.norm <= MATRIX_TOLERANCE) return null
        return normal.unit.canonicalDirection()
    }
}

private data class RotationGroup(
    val family: PointGroupFamily,
    val fold: Int? = null,
)

private fun classifyRotationGroup(transforms: List<OrthogonalTransform>): RotationGroup {
    val maxOrder = transforms.maxOf(OrthogonalTransform::rotationOrder)
    return when {
        transforms.size == 60 && maxOrder == 5 -> RotationGroup(PointGroupFamily.Icosahedral)
        transforms.size == 24 && maxOrder == 4 -> RotationGroup(PointGroupFamily.Octahedral)
        transforms.size == 12 && maxOrder == 3 -> RotationGroup(PointGroupFamily.Tetrahedral)
        transforms.size == 2 * maxOrder -> RotationGroup(PointGroupFamily.Dihedral, maxOrder)
        transforms.size == maxOrder -> RotationGroup(PointGroupFamily.Cyclic, maxOrder)
        else -> error("Unsupported rotation group: ${transforms.size} operations, maximum order $maxOrder")
    }
}

private fun classifyPointGroup(
    rotationGroup: RotationGroup,
    hasOrientationReversingOperations: Boolean,
    reflectionPlanes: Int,
): PointGroup {
    if (!hasOrientationReversingOperations) {
        return PointGroup(rotationGroup.family, rotationGroup.fold)
    }
    val fold = rotationGroup.fold
    val suffix = when (rotationGroup.family) {
        PointGroupFamily.Cyclic -> when (reflectionPlanes) {
            0 -> PointGroupSuffix.ImproperRotation
            1 -> PointGroupSuffix.Horizontal
            fold -> PointGroupSuffix.Vertical
            else -> error("Unsupported cyclic point group: $reflectionPlanes reflection planes")
        }
        PointGroupFamily.Dihedral -> when (reflectionPlanes) {
            fold -> PointGroupSuffix.Diagonal
            requireNotNull(fold) + 1 -> PointGroupSuffix.Horizontal
            else -> error("Unsupported dihedral point group: $reflectionPlanes reflection planes")
        }
        PointGroupFamily.Tetrahedral -> when (reflectionPlanes) {
            3 -> PointGroupSuffix.Horizontal
            6 -> PointGroupSuffix.Diagonal
            else -> error("Unsupported tetrahedral point group: $reflectionPlanes reflection planes")
        }
        PointGroupFamily.Octahedral -> PointGroupSuffix.Horizontal.also {
            check(reflectionPlanes == 9) { "Unsupported octahedral point group: $reflectionPlanes reflection planes" }
        }
        PointGroupFamily.Icosahedral -> PointGroupSuffix.Horizontal.also {
            check(reflectionPlanes == 15) { "Unsupported icosahedral point group: $reflectionPlanes reflection planes" }
        }
    }
    return PointGroup(rotationGroup.family, fold, suffix)
}

private fun OrthogonalTransform.rotationOrder(): Int {
    val angle = acos(((trace - 1.0) / 2.0).coerceIn(-1.0, 1.0))
    if (angle <= MATRIX_TOLERANCE) return 1
    for (order in 2..MAX_ROTATION_ORDER) {
        val turns = order * angle / (2.0 * PI)
        if (abs(turns - round(turns)) <= MATRIX_TOLERANCE) return order
    }
    error("Cannot determine rotation order for angle $angle")
}

private data class SymmetryOrbits(
    val faces: List<List<Int>>,
    val edges: List<List<Int>>,
    val vertices: List<List<Int>>,
) {
    val counts: FEV get() = FEV(faces.size, edges.size, vertices.size)
}

private fun Polyhedron.symmetryOrbitCounts(operations: List<GeometricSymmetryOperation>): FEV =
    symmetryOrbits(operations).counts

private fun Polyhedron.symmetryOrbits(operations: List<GeometricSymmetryOperation>): SymmetryOrbits {
    val vertexSets = DisjointSets(vs.size)
    val edgeSets = DisjointSets(es.size)
    val faceSets = DisjointSets(fs.size)
    val topology = SymmetryTopology(this)
    fun merge(permutation: IntArray, orientation: Int) {
        for (vertex in vs) vertexSets.union(vertex.id, permutation[vertex.id])
        for ((edgeIndex, edge) in es.withIndex()) {
            val target = requireNotNull(topology.edgeIndex(permutation[edge.a.id], permutation[edge.b.id]))
            edgeSets.union(edgeIndex, target)
        }
        for ((faceIndex, face) in fs.withIndex()) {
            faceSets.union(faceIndex, requireNotNull(topology.targetFaceId(permutation, face, orientation)))
        }
    }
    for (operation in operations) merge(operation.vertexPermutation, operation.orientation)
    if (isCompound) {
        // Completely coincident members can be exchanged by the identity rotation. Count these
        // lifts in element orbits, but never as extra rotations in the geometric point group.
        val members = componentPolyhedra()
        val globalIds = components.map { faces -> faces.flatMap { it.fvs }.map { it.id }.distinct().sorted() }
        val identity = operations.first { abs(it.transform.trace - 3.0) < MATRIX_TOLERANCE }.transform
        val tolerance = circumradius.coerceAtLeast(1.0) * SYMMETRY_TOLERANCE
        for (a in members.indices) for (b in a + 1 until members.size) {
            val mapping = members[a].geometricVertexPermutation(members[b], identity, 1, tolerance) ?: continue
            val exchange = IntArray(vs.size) { it }
            for (i in mapping.indices) {
                val first = globalIds[a][i]
                val second = globalIds[b][mapping[i]]
                exchange[first] = second
                exchange[second] = first
            }
            merge(exchange, 1)
        }
    }
    return SymmetryOrbits(faceSets.groups, edgeSets.groups, vertexSets.groups)
}

private class DisjointSets(size: Int) {
    private val parent = IntArray(size) { it }

    fun union(first: Int, second: Int) {
        val firstRoot = root(first)
        val secondRoot = root(second)
        if (firstRoot != secondRoot) parent[secondRoot] = firstRoot
    }

    private fun root(index: Int): Int {
        var result = index
        while (parent[result] != result) result = parent[result]
        var current = index
        while (parent[current] != current) {
            val next = parent[current]
            parent[current] = result
            current = next
        }
        return result
    }

    val groups: List<List<Int>>
        get() = parent.indices
            .groupBy(::root)
            .values
            .map(List<Int>::sorted)
            .sortedBy { group -> group.first() }
}

private data class SpatialKey(val x: Long, val y: Long, val z: Long)

private class VertexSpatialIndex(vertices: List<Vertex>, tolerance: Double) {
    private val cellSize = tolerance * 2.0
    private val toleranceSquared = tolerance * tolerance
    private val buckets = vertices.groupBy { vertex -> vertex.spatialKey() }

    fun find(point: Vec3): Int? {
        val center = point.spatialKey()
        buckets[center]?.firstOrNull { candidate -> point.near(candidate) }?.let { return it.id }
        for (dx in -1L..1L) for (dy in -1L..1L) for (dz in -1L..1L) {
            if (dx == 0L && dy == 0L && dz == 0L) continue
            for (candidate in buckets[SpatialKey(center.x + dx, center.y + dy, center.z + dz)].orEmpty()) {
                if (point.near(candidate)) return candidate.id
            }
        }
        return null
    }

    private fun Vec3.near(candidate: Vertex): Boolean {
        val x = this.x - candidate.x
        val y = this.y - candidate.y
        val z = this.z - candidate.z
        return x * x + y * y + z * z <= toleranceSquared
    }

    private fun Vec3.spatialKey() = SpatialKey(
        floor(x / cellSize).toLong(),
        floor(y / cellSize).toLong(),
        floor(z / cellSize).toLong(),
    )
}

private fun Sequence<Vec3>.distinctDirections(): Sequence<Vec3> {
    val directions = ArrayList<Vec3>()
    return filter { direction ->
        if (directions.any { existing -> abs(existing * direction) >= 1.0 - MATRIX_TOLERANCE }) {
            false
        } else {
            directions += direction
            true
        }
    }
}

private fun Vec3.canonicalDirection(): Vec3 {
    val firstNonZero = listOf(x, y, z).firstOrNull { abs(it) > MATRIX_TOLERANCE } ?: return this
    return if (firstNonZero < 0.0) this * -1.0 else this
}

private fun Vec3.toMutableDirection() = MutableVec3(x, y, z)

private fun edgeKey(first: Int, second: Int): Long {
    val low = minOf(first, second).toLong()
    val high = maxOf(first, second).toLong()
    return (low shl 32) xor high
}
