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
    private val tolerance = maxOf(poly.circumradius, 1.0) * 1e-8
    private val edgeDirections = poly.directedEdges.associate { edge ->
        directedEdgeKey(edge) to if (hasMaterial(edge.r) && hasMaterial(edge.l)) {
            solve(listOf(edge.r.outwardNormal, edge.l.outwardNormal))
        } else {
            edge.r.outwardNormal
        }
    }
    private val vertexDirections = buildMap {
        for (face in poly.fs) for (index in face.fvs.indices) {
            val previous = face.sourceEdge((index + face.size - 1) % face.size)
            val next = face.sourceEdge(index)
            val normals = buildList {
                add(face.outwardNormal)
                if (hasMaterial(previous.l)) add(previous.l.outwardNormal)
                if (hasMaterial(next.l)) add(next.l.outwardNormal)
            }
            put(face.id to face[index].id, solve(normals))
        }
    }

    fun direction(face: Face, point: Vec3): Vec3 {
        face.fvs.firstOrNull { vertex -> (vertex - point).norm <= tolerance }?.let { vertex ->
            return vertexDirections.getValue(face.id to vertex.id)
        }
        sourceEdgeOrNull(face, point)?.let(::edgeDirection)?.let { return it }
        return face.outwardNormal
    }

    fun direction(face: Face, point: Vec3, sourceSegmentIndex: Int): Vec3 {
        face.fvs.firstOrNull { vertex -> (vertex - point).norm <= tolerance }?.let { vertex ->
            return vertexDirections.getValue(face.id to vertex.id)
        }
        return edgeDirection(face.sourceEdge(sourceSegmentIndex))
    }

    fun sourceEdgeOrNull(face: Face, point: Vec3): Edge? =
        face.directedEdges.firstOrNull { edge -> point.isOnSegment(edge.a, edge.b) }

    fun sourceEdgeOrNull(face: Face, a: Vec3, b: Vec3): Edge? =
        face.directedEdges.firstOrNull { edge ->
            a.isOnSegment(edge.a, edge.b) && b.isOnSegment(edge.a, edge.b)
        }

    fun vertexDirection(face: Face, vertex: Vertex): Vec3 =
        vertexDirections.getValue(face.id to vertex.id)

    fun edgeDirection(edge: Edge): Vec3 = edgeDirections.getValue(directedEdgeKey(edge))

    /** In-face distance from the source edge to the unit-thickness inner bisector line. */
    fun rimFactor(edge: Edge): Double {
        if (!hasMaterial(edge.r) || !hasMaterial(edge.l)) return 0.0
        val direction = edgeDirection(edge)
        return sqrt((direction * direction - 1.0).coerceAtLeast(0.0))
    }

    fun effectiveRimWidths(face: Face, rim: Double, width: Double): List<Double> =
        face.fvs.indices.map { index -> maxOf(rim, width * rimFactor(face.sourceEdge(index))) }

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

/** Minimum-norm displacement whose projection onto every supplied unit normal is one. */
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

    var best: Vec3? = null
    var bestResidual = Double.POSITIVE_INFINITY
    for (first in 0 until distinct.size - 2) {
        for (second in first + 1 until distinct.size - 1) {
            for (third in second + 1 until distinct.size) {
                val a = distinct[first]
                val b = distinct[second]
                val c = distinct[third]
                val determinant = a * (b cross c)
                if (abs(determinant) <= EPS) continue
                val candidate = ((b cross c) + (c cross a) + (a cross b)) * (1.0 / determinant)
                val residual = distinct.maxOf { normal -> abs(normal * candidate - 1.0) }
                if (residual < bestResidual) {
                    best = candidate
                    bestResidual = residual
                }
            }
        }
    }
    return best ?: distinct.first()
}

private fun solvePair(first: Vec3, second: Vec3): Vec3 {
    val cosine = first * second
    val denominator = 1.0 + cosine
    if (abs(denominator) <= EPS) return first
    return (first + second) * (1.0 / denominator)
}
