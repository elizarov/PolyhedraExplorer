package polyhedra.model.poly

import polyhedra.model.util.EPS
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.plus
import polyhedra.model.util.times
import kotlin.math.abs

/**
 * Directions which turn a unit perpendicular face offset into watertight joins at source edges.
 *
 * The face being thickened contributes `normal dot direction = 1`, preserving its requested
 * perpendicular thickness. Every neighboring face contributes `normal dot direction = 0`, clipping
 * that face strip to the original polyhedron boundary instead of letting acute joins protrude.
 */
class FaceThicknessJoins(
    poly: Polyhedron,
    private val materialFaceIds: Set<Int>,
) {
    private val tolerance = maxOf(poly.circumradius, 1.0) * 1e-8
    private val vertexDirections = buildMap {
        for (vertex in poly.vs) {
            val incident = vertex.directedEdges.flatMap { edge -> listOf(edge.l, edge.r) }
                .distinctBy(Face::id)
            for (face in incident) {
                put(
                    face.id to vertex.id,
                    solveClipped(face.outwardNormal, incident.filter { candidate -> candidate != face }
                        .map(Face::outwardNormal)),
                )
            }
        }
    }
    private val edgeDirections = poly.directedEdges.associate { edge ->
        directedEdgeKey(edge) to solveClipped(edge.r.outwardNormal, listOf(edge.l.outwardNormal))
    }

    fun direction(face: Face, point: Vec3): Vec3 {
        face.fvs.firstOrNull { vertex -> (vertex - point).norm <= tolerance }?.let { vertex ->
            return vertexDirections.getValue(face.id to vertex.id)
        }
        sourceEdgeOrNull(face, point)?.let { edge ->
            return edgeDirections.getValue(directedEdgeKey(edge))
        }
        return face.outwardNormal
    }

    fun sourceEdgeOrNull(face: Face, point: Vec3): Edge? =
        face.directedEdges.firstOrNull { edge -> point.isOnSegment(edge.a, edge.b) }

    fun sourceEdgeOrNull(face: Face, a: Vec3, b: Vec3): Edge? =
        face.directedEdges.firstOrNull { edge -> a.isOnSegment(edge.a, edge.b) && b.isOnSegment(edge.a, edge.b) }

    fun vertexDirection(face: Face, vertex: Vertex): Vec3 = vertexDirections.getValue(face.id to vertex.id)

    fun edgeDirection(edge: Edge): Vec3 = edgeDirections.getValue(directedEdgeKey(edge))

    fun hasMaterial(face: Face): Boolean = face.id in materialFaceIds

    fun isExposed(edge: Edge): Boolean = hasMaterial(edge.r) && !hasMaterial(edge.l)

    private fun Vec3.isOnSegment(a: Vec3, b: Vec3): Boolean {
        val edge = b - a
        val lengthSquared = edge * edge
        if (lengthSquared <= EPS) return false
        val along = ((this - a) * edge) / lengthSquared
        if (along < -tolerance || along > 1.0 + tolerance) return false
        return ((this - a) cross edge).norm <= tolerance * edge.norm
    }
}

val Face.outwardNormal: Vec3
    get() = if (d >= 0.0) this else Vec3(-x, -y, -z)

private data class Constraint(val normal: Vec3, val offset: Double)

private fun directedEdgeKey(edge: Edge) = edge.r.id to edge.a.id

/** Minimum displacement that keeps unit thickness and does not cross any neighboring plane. */
private fun solveClipped(primary: Vec3, neighbors: List<Vec3>): Vec3 {
    val candidates = arrayListOf(primary)
    for (first in neighbors.indices) {
        candidates += solve(listOf(Constraint(primary, 1.0), Constraint(neighbors[first], 0.0)))
        for (second in first + 1 until neighbors.size) {
            candidates += solve(listOf(
                Constraint(primary, 1.0),
                Constraint(neighbors[first], 0.0),
                Constraint(neighbors[second], 0.0),
            ))
        }
    }
    return candidates.filter { candidate ->
        abs(primary * candidate - 1.0) <= 1e-8 &&
            neighbors.all { neighbor -> neighbor * candidate >= -1e-8 }
    }.minByOrNull(Vec3::norm) ?: primary
}

private fun solve(constraints: List<Constraint>): Vec3 {
    val distinct = constraints.distinctBy { constraint ->
        listOf(
            (constraint.normal.x * 1e10).toLong(),
            (constraint.normal.y * 1e10).toLong(),
            (constraint.normal.z * 1e10).toLong(),
            (constraint.offset * 1e10).toLong(),
        )
    }
    if (distinct.isEmpty()) return Vec3.ZERO
    if (distinct.size == 1) return distinct.single().normal * distinct.single().offset
    if (distinct.size == 2) return solvePair(distinct[0], distinct[1])

    var best: Vec3? = null
    var bestResidual = Double.POSITIVE_INFINITY
    var bestDeterminant = 0.0
    for (first in 0 until distinct.size - 2) {
        for (second in first + 1 until distinct.size - 1) {
            for (third in second + 1 until distinct.size) {
                val a = distinct[first]
                val b = distinct[second]
                val c = distinct[third]
                val determinant = a.normal * (b.normal cross c.normal)
                if (abs(determinant) <= EPS) continue
                val candidate = (
                    (b.normal cross c.normal) * a.offset +
                        (c.normal cross a.normal) * b.offset +
                        (a.normal cross b.normal) * c.offset
                    ) * (1.0 / determinant)
                val residual = distinct.maxOf { constraint ->
                    abs(constraint.normal * candidate - constraint.offset)
                }
                if (residual < bestResidual - EPS ||
                    (abs(residual - bestResidual) <= EPS && abs(determinant) > bestDeterminant)
                ) {
                    best = candidate
                    bestResidual = residual
                    bestDeterminant = abs(determinant)
                }
            }
        }
    }
    if (best != null) return best

    return distinct.indices.asSequence().flatMap { first ->
        (first + 1 until distinct.size).asSequence().map { second ->
            solvePair(distinct[first], distinct[second])
        }
    }.minByOrNull { candidate ->
        distinct.sumOf { constraint -> abs(constraint.normal * candidate - constraint.offset) }
    } ?: distinct.first().normal * distinct.first().offset
}

private fun solvePair(first: Constraint, second: Constraint): Vec3 {
    val cosine = first.normal * second.normal
    val denominator = 1.0 - cosine * cosine
    if (abs(denominator) <= EPS) {
        return first.normal * ((first.offset + second.offset) / 2.0)
    }
    val firstWeight = (first.offset - cosine * second.offset) / denominator
    val secondWeight = (second.offset - cosine * first.offset) / denominator
    return first.normal * firstWeight + second.normal * secondWeight
}
