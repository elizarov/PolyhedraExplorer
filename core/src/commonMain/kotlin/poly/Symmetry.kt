/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core.poly

import polyhedra.model.api.CoreSymmetry
import polyhedra.model.api.SymmetryFamily
import polyhedra.model.api.SymmetryGroup
import polyhedra.model.poly.Edge
import polyhedra.model.poly.FEV
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.Vertex
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
    val radius = circumradius.coerceAtLeast(1.0)
    val tolerance = radius * SYMMETRY_TOLERANCE
    val vertexIndex = VertexSpatialIndex(vs, tolerance)
    val sourceEdge = directedEdges.firstOrNull { edgeFrame(it, tolerance) != null }
        ?: error("Cannot derive a symmetry frame from $this")
    val sourceFrame = requireNotNull(edgeFrame(sourceEdge, tolerance))

    val proper = ArrayList<SymmetryOperation>()
    var improperSeed: OrthogonalTransform? = null
    for (targetEdge in directedEdges) {
        val targetFrame = edgeFrame(targetEdge, tolerance) ?: continue
        if (sourceEdge.hasMatchingGeometry(targetEdge, tolerance, reverseSides = false)) {
            symmetryOperation(sourceFrame, targetFrame, orientation = 1, vertexIndex)
                ?.let(proper::add)
        }
        if (improperSeed == null && sourceEdge.hasMatchingGeometry(targetEdge, tolerance, reverseSides = true)) {
            improperSeed = symmetryOperation(sourceFrame, targetFrame, orientation = -1, vertexIndex)?.transform
        }
    }
    check(proper.isNotEmpty()) { "Every polyhedron must have the identity symmetry" }

    val group = classifyRotationGroup(proper.map { it.transform })
    val axisDirections = proper.asSequence()
        .mapNotNull { operation -> operation.transform.rotationAxisOrNull() }
        .distinctDirections()
        .map(Vec3::toMutableDirection)
        .toList()
    val planeNormals = improperSeed?.let { reversing ->
        proper.asSequence().map { operation -> operation.transform * reversing }
    }.orEmpty()
        .mapNotNull(OrthogonalTransform::reflectionPlaneNormalOrNull)
        .distinctDirections()
        .map(Vec3::toMutableDirection)
        .toList()
    return CoreSymmetry(
        group = group,
        orbitCounts = symmetryOrbitCounts(proper),
        reflectionPlaneNormals = planeNormals,
        rotationAxisDirections = axisDirections,
    )
}

private data class OrthonormalFrame(
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

private data class SymmetryOperation(
    val transform: OrthogonalTransform,
    val vertexPermutation: IntArray,
)

private fun Polyhedron.symmetryOperation(
    source: OrthonormalFrame,
    target: OrthonormalFrame,
    orientation: Int,
    vertexIndex: VertexSpatialIndex,
): SymmetryOperation? {
    val transform = OrthogonalTransform(source, target, orientation)
    val permutation = IntArray(vs.size)
    for (vertex in vs) {
        permutation[vertex.id] = vertexIndex.find(transform(vertex)) ?: return null
    }
    return SymmetryOperation(transform, permutation)
}

private class OrthogonalTransform(
    val xx: Double, val xy: Double, val xz: Double,
    val yx: Double, val yy: Double, val yz: Double,
    val zx: Double, val zy: Double, val zz: Double,
) {
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

private fun classifyRotationGroup(transforms: List<OrthogonalTransform>): SymmetryGroup {
    val maxOrder = transforms.maxOf(OrthogonalTransform::rotationOrder)
    return when {
        transforms.size == 60 && maxOrder == 5 -> SymmetryGroup(SymmetryFamily.Icosahedral)
        transforms.size == 24 && maxOrder == 4 -> SymmetryGroup(SymmetryFamily.Octahedral)
        transforms.size == 12 && maxOrder == 3 -> SymmetryGroup(SymmetryFamily.Tetrahedral)
        transforms.size == 2 * maxOrder -> SymmetryGroup(SymmetryFamily.Dihedral, maxOrder)
        transforms.size == maxOrder -> SymmetryGroup(SymmetryFamily.Cyclic, maxOrder)
        else -> error("Unsupported rotation group: ${transforms.size} operations, maximum order $maxOrder")
    }
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

private fun Polyhedron.symmetryOrbitCounts(operations: List<SymmetryOperation>): FEV {
    val vertexSets = DisjointSets(vs.size)
    val edgeSets = DisjointSets(es.size)
    val faceSets = DisjointSets(fs.size)
    val edgesByKey = es.withIndex().associate { (index, edge) -> edgeKey(edge.a.id, edge.b.id) to index }
    val facesByVertices = fs.withIndex().associate { (index, face) ->
        face.fvs.map(Vertex::id).sorted() to index
    }
    for (operation in operations) {
        val permutation = operation.vertexPermutation
        for (vertex in vs) vertexSets.union(vertex.id, permutation[vertex.id])
        for ((edgeIndex, edge) in es.withIndex()) {
            val target = edgesByKey.getValue(edgeKey(permutation[edge.a.id], permutation[edge.b.id]))
            edgeSets.union(edgeIndex, target)
        }
        for ((faceIndex, face) in fs.withIndex()) {
            val targetVertices = face.fvs.map { vertex -> permutation[vertex.id] }.sorted()
            faceSets.union(faceIndex, facesByVertices.getValue(targetVertices))
        }
    }
    return FEV(faceSets.count, edgeSets.count, vertexSets.count)
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

    val count: Int get() = parent.indices.count { root(it) == it }
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
