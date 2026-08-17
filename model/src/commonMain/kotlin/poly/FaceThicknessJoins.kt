package polyhedra.model.poly

import polyhedra.model.util.EPS
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.plus
import polyhedra.model.util.times
import kotlin.math.abs
import kotlin.math.sqrt

/** Whether an immersed or folded presentation must retain the configured top-rim width. */
fun Polyhedron.keepsConfiguredRimWidth(face: Face): Boolean =
    !face.isPlanar || resolvedFaces.any(ResolvedFaceGeometry::sourceBoundarySelfIntersects)

/**
 * Equal-distance inner face joins for a thick polyhedral surface.
 *
 * The inner planes of two material faces are offset by the same perpendicular distance. Their
 * intersection is therefore the dihedral-angle bisector. A hidden-face opening must be inset at
 * least as far as that inner intersection projects onto its face so its opening wall can remain
 * perpendicular to the face without crossing the inner surface.
 */
class FaceThicknessJoins(
    private val poly: Polyhedron,
    private val materialFaceIds: Set<Int> = poly.fs.mapTo(linkedSetOf(), Face::id),
) {
    init {
        require(materialFaceIds.all { faceId -> faceId in poly.fs.indices })
    }

    private val tolerance = maxOf(poly.circumradius, 1.0) * 1e-8
    private val edgeDirections = poly.directedEdges.associate { edge ->
        directedEdgeKey(edge) to if (hasMaterial(edge.r) && hasMaterial(edge.l)) {
            solve(listOf(edge.r.outwardNormal, edge.l.outwardNormal))
        } else {
            edge.r.outwardNormal
        }
    }
    private val vertexDirections = buildMap {
        for (vertex in poly.vs) {
            val normals = poly.fs.asSequence()
                .filter { face -> hasMaterial(face) && vertex in face.fvs }
                .map(Face::outwardNormal)
                .toList()
            if (normals.isNotEmpty()) put(vertex.id, solveInteriorJoin(normals))
        }
    }

    fun direction(face: Face, point: Vec3): Vec3 {
        face.fvs.firstOrNull { vertex -> (vertex - point).norm <= tolerance }?.let { vertex ->
            return vertexDirection(face, vertex)
        }
        sourceEdgeOrNull(face, point)?.let { edge -> return edgeDirection(face, edge) }
        return face.outwardNormal
    }

    fun direction(face: Face, point: Vec3, sourceSegmentIndex: Int): Vec3 {
        face.fvs.firstOrNull { vertex -> (vertex - point).norm <= tolerance }?.let { vertex ->
            return vertexDirection(face, vertex)
        }
        val edge = face.sourceEdge(sourceSegmentIndex)
        return edgeDirection(face, edge)
    }

    fun sourceEdgeOrNull(face: Face, point: Vec3): Edge? =
        face.directedEdges.firstOrNull { edge -> point.isOnSegment(edge.a, edge.b) }

    fun sourceEdgeOrNull(face: Face, a: Vec3, b: Vec3): Edge? =
        face.directedEdges.firstOrNull { edge ->
            a.isOnSegment(edge.a, edge.b) && b.isOnSegment(edge.a, edge.b)
        }

    fun sourceEdge(face: Face, index: Int): Edge = face.sourceEdge(index)

    fun vertexDirection(face: Face, vertex: Vertex): Vec3 =
        vertexDirections[vertex.id] ?: face.outwardNormal

    fun edgeDirection(edge: Edge): Vec3 = edgeDirections.getValue(directedEdgeKey(edge))

    fun edgeDirection(face: Face, edge: Edge): Vec3 =
        if (hasMaterial(face)) edgeDirection(edge) else face.outwardNormal

    /** In-face distance from the source edge to the unit-thickness inner bisector line. */
    fun rimFactor(edge: Edge): Double {
        if (!hasMaterial(edge.r) || !hasMaterial(edge.l)) return 0.0
        val direction = edgeDirection(edge)
        return sqrt((direction * direction - 1.0).coerceAtLeast(0.0))
    }

    fun effectiveRimWidths(face: Face, rim: Double, width: Double): List<Double> {
        if (poly.keepsConfiguredRimWidth(face)) return List(face.size) { rim }
        return requiredRimWidths(face, width).map { required -> maxOf(rim, required) }
    }

    /** Per-edge top inset needed for a perpendicular opening wall to reach the inner miter. */
    fun requiredRimWidths(face: Face, width: Double): List<Double> {
        require(width.isFinite() && width >= 0.0)
        return face.fvs.indices.map { index ->
            val edge = face.sourceEdge(index)
            val direction = edgeDirection(face, edge)
            val normalComponent = face.outwardNormal * direction
            val tangent = direction - face.outwardNormal * normalComponent
            width * tangent.norm
        }
    }

    private fun hasMaterial(face: Face): Boolean = face.id in materialFaceIds

    private fun Vec3.isOnSegment(a: Vec3, b: Vec3): Boolean {
        val edge = b - a
        val lengthSquared = edge * edge
        if (lengthSquared <= EPS) return false
        val along = ((this - a) * edge) / lengthSquared
        if (along < -tolerance || along > 1.0 + tolerance) return false
        return ((this - a) cross edge).norm <= tolerance * edge.norm
    }
}

private fun Face.sourceEdge(index: Int): Edge {
    val a = fvs[index]
    val b = fvs[(index + 1) % size]
    return directedEdges.single { edge -> edge.a == a && edge.b == b }
}

val Face.outwardNormal: Vec3
    get() = if (d >= 0.0) this else Vec3(-x, -y, -z)

private fun directedEdgeKey(edge: Edge) = edge.r.id to edge.a.id

/** Unit-offset displacement; overdetermined vertex fans use a stable least-squares bevel point. */
private fun solve(normals: List<Vec3>): Vec3 {
    val distinct = normals.distinctBy { normal ->
        listOf(
            (normal.x * 1e10).toLong(),
            (normal.y * 1e10).toLong(),
            (normal.z * 1e10).toLong(),
        )
    }
    if (distinct.size == 1) return distinct.single()
    if (distinct.size == 2) return solvePair(distinct[0], distinct[1])
    return solveLeastSquares(distinct)
}

private fun solveLeastSquares(normals: List<Vec3>): Vec3 {
    val xx = normals.sumOf { it.x * it.x }
    val xy = normals.sumOf { it.x * it.y }
    val xz = normals.sumOf { it.x * it.z }
    val yy = normals.sumOf { it.y * it.y }
    val yz = normals.sumOf { it.y * it.z }
    val zz = normals.sumOf { it.z * it.z }
    val rhs = Vec3(normals.sumOf(Vec3::x), normals.sumOf(Vec3::y), normals.sumOf(Vec3::z))
    var regularization = 0.0
    repeat(6) {
        val a = xx + regularization
        val e = yy + regularization
        val i = zz + regularization
        val determinant = a * (e * i - yz * yz) - xy * (xy * i - yz * xz) +
            xz * (xy * yz - e * xz)
        if (abs(determinant) > EPS) {
            val dx = rhs.x * (e * i - yz * yz) - xy * (rhs.y * i - yz * rhs.z) +
                xz * (rhs.y * yz - e * rhs.z)
            val dy = a * (rhs.y * i - yz * rhs.z) - rhs.x * (xy * i - yz * xz) +
                xz * (xy * rhs.z - rhs.y * xz)
            val dz = a * (e * rhs.z - rhs.y * yz) - xy * (xy * rhs.z - rhs.y * xz) +
                rhs.x * (xy * yz - e * xz)
            return Vec3(dx / determinant, dy / determinant, dz / determinant)
        }
        regularization = if (regularization == 0.0) 1e-10 else regularization * 100.0
    }
    return normals.first()
}

/** Minimum local displacement that lies behind every incident unit-offset face plane. */
private fun solveInteriorJoin(normals: List<Vec3>): Vec3 {
    val distinct = normals.distinctBy { normal ->
        listOf(
            (normal.x * 1e10).toLong(),
            (normal.y * 1e10).toLong(),
            (normal.z * 1e10).toLong(),
        )
    }
    val candidates = arrayListOf<Vec3>()
    candidates += distinct
    for (first in distinct.indices) for (second in first + 1 until distinct.size) {
        candidates += solvePair(distinct[first], distinct[second])
    }
    for (first in 0 until distinct.size - 2) {
        for (second in first + 1 until distinct.size - 1) {
            for (third in second + 1 until distinct.size) {
                solveThree(distinct[first], distinct[second], distinct[third])?.let(candidates::add)
            }
        }
    }
    return candidates.asSequence()
        .filter { candidate -> distinct.all { normal -> normal * candidate >= 1.0 - 1e-9 } }
        .minByOrNull(Vec3::norm)
        ?: solveLeastSquares(distinct)
}

private fun solveThree(first: Vec3, second: Vec3, third: Vec3): Vec3? {
    val determinant = first * (second cross third)
    if (abs(determinant) <= EPS) return null
    return ((second cross third) + (third cross first) + (first cross second)) * (1.0 / determinant)
}

private fun solvePair(first: Vec3, second: Vec3): Vec3 {
    val cosine = first * second
    val denominator = 1.0 + cosine
    if (abs(denominator) <= EPS) return first
    return (first + second) * (1.0 / denominator)
}
