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

/** Whether immersed or folded presentation faces must retain the user-configured top-rim width. */
val Polyhedron.keepsConfiguredRimWidth: Boolean
    get() = nonPlanarFaceKinds.isNotEmpty() ||
        resolvedFaces.any(ResolvedFaceGeometry::sourceBoundarySelfIntersects)

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
    private val rimFaceIds: Set<Int> = emptySet(),
    private val rimWidth: Double = 0.0,
    private val resolvedRims: Map<Int, ResolvedRimGeometry> = emptyMap(),
) {
    init {
        require(rimFaceIds.all(materialFaceIds::contains))
        require(rimWidth.isFinite() && rimWidth >= 0.0)
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

    fun direction(face: Face, point: Vec3, width: Double = 0.0): Vec3 {
        face.fvs.firstOrNull { vertex -> (vertex - point).norm <= tolerance }?.let { vertex ->
            return vertexDirection(face, vertex, width)
        }
        sourceEdgeOrNull(face, point)?.let { edge -> return edgeDirection(edge, point, width) }
        return face.outwardNormal
    }

    fun direction(face: Face, point: Vec3, sourceSegmentIndex: Int, width: Double = 0.0): Vec3 {
        face.fvs.firstOrNull { vertex -> (vertex - point).norm <= tolerance }?.let { vertex ->
            return vertexDirection(face, vertex, width)
        }
        val edge = face.sourceEdge(sourceSegmentIndex)
        return edgeDirection(edge, point, width)
    }

    fun sourceEdgeOrNull(face: Face, point: Vec3): Edge? =
        face.directedEdges.firstOrNull { edge -> point.isOnSegment(edge.a, edge.b) }

    fun sourceEdgeOrNull(face: Face, a: Vec3, b: Vec3): Edge? =
        face.directedEdges.firstOrNull { edge ->
            a.isOnSegment(edge.a, edge.b) && b.isOnSegment(edge.a, edge.b)
        }

    fun vertexDirection(face: Face, vertex: Vertex, width: Double = 0.0): Vec3 {
        val direction = vertexDirections.getValue(face.id to vertex.id)
        val incident = vertex.directedEdges.flatMap { edge -> listOf(edge.l, edge.r) }
            .distinctBy(Face::id)
            .filter(::hasMaterial)
        return direction.boundedAt(vertex, incident, width)
    }

    fun edgeDirection(edge: Edge, width: Double = 0.0): Vec3 =
        edgeDirection(edge, (edge.a + edge.b) * 0.5, width)

    private fun edgeDirection(edge: Edge, point: Vec3, width: Double): Vec3 {
        val direction = edgeDirections.getValue(directedEdgeKey(edge))
        val incident = listOf(edge.r, edge.l).filter(::hasMaterial)
        return direction.boundedAt(point, incident, width)
    }

    /** In-face distance from the source edge to the unit-thickness inner bisector line. */
    fun rimFactor(edge: Edge): Double {
        if (!hasMaterial(edge.r) || !hasMaterial(edge.l)) return 0.0
        val direction = edgeDirection(edge)
        return sqrt((direction * direction - 1.0).coerceAtLeast(0.0))
    }

    fun effectiveRimWidths(face: Face, rim: Double, width: Double): List<Double> {
        if (poly.keepsConfiguredRimWidth) return List(face.size) { rim }
        return face.fvs.indices.map { index ->
            val edge = face.sourceEdge(index)
            val direction = edgeDirection(edge, width)
            val normalComponent = face.outwardNormal * direction
            val tangent = direction - face.outwardNormal * normalComponent
            maxOf(rim, width * tangent.norm)
        }
    }

    private fun hasMaterial(face: Face): Boolean = face.id in materialFaceIds

    /**
     * Keeps an exact equal-offset join until that join reaches the first boundary of any incident
     * material face. Past a local offset-surface collapse the corner stays at that shared boundary
     * instead of continuing through the face and producing an inverted, detached sheet.
     */
    private fun Vec3.boundedAt(point: Vec3, incident: List<Face>, width: Double): Vec3 {
        if (width <= tolerance || incident.isEmpty()) return this
        val scale = incident.minOf { face -> face.maximumFilledPrefix(point, this, width) }
        return this * scale
    }

    private fun Face.maximumFilledPrefix(point: Vec3, direction: Vec3, width: Double): Double {
        val normal = outwardNormal
        val normalComponent = normal * direction
        val tangent = direction - normal * normalComponent
        val resolvedRim = resolvedRims[id].takeIf { isPlanar }
        val rimScale = if (id in rimFaceIds && tangent.norm * width > rimWidth) {
            rimWidth / (tangent.norm * width)
        } else {
            1.0
        }
        if (!isPlanar || rimScale <= 0.0) return rimScale
        val delta = tangent * -width
        if (delta.norm <= tolerance) return 1.0
        val geometry = poly.resolvedFaces[id]
        val boundaries = sequence {
            geometry.edges.asSequence().filter { edge -> !edge.internalToFill }.forEach { edge ->
                yield(geometry.vertices[edge.a].position to geometry.vertices[edge.b].position)
            }
            resolvedRim?.regions?.forEach { region ->
                (listOf(region.outer) + region.holes).forEach { cycle ->
                    cycle.vertices.indices.forEach { index ->
                        yield(cycle.vertices[index] to cycle.vertices[(index + 1) % cycle.vertices.size])
                    }
                }
            }
        }
        val events = boundaries.mapNotNull { (a, b) ->
                val segment = b - a
                val denominator = (delta cross segment) * normal
                if (abs(denominator) <= tolerance * delta.norm * segment.norm) return@mapNotNull null
                val offset = a - point
                val parameter = ((offset cross segment) * normal) / denominator
                val along = ((offset cross delta) * normal) / denominator
                parameter.takeIf {
                    it > 1e-10 && it < 1.0 - 1e-10 && along >= -1e-9 && along <= 1.0 + 1e-9
                }
            }
            .sorted()
            .distinctBy { parameter -> (parameter * 1e10).toLong() }
            .toList()
        var start = 0.0
        for (end in events + 1.0) {
            if (end - start > 1e-10) {
                val sample = point + delta * ((start + end) * 0.5)
                if (
                    !geometry.contains(sample, normal, tolerance) ||
                    resolvedRim?.containsProjected(sample, normal, tolerance) == false
                ) return minOf(start, rimScale)
            }
            start = end
        }
        return rimScale
    }

    private fun Vec3.isOnSegment(a: Vec3, b: Vec3): Boolean {
        val edge = b - a
        val lengthSquared = edge * edge
        if (lengthSquared <= EPS) return false
        val along = ((this - a) * edge) / lengthSquared
        if (along < -tolerance || along > 1.0 + tolerance) return false
        return ((this - a) cross edge).norm <= tolerance * edge.norm
    }
}

private fun ResolvedFaceGeometry.contains(point: Vec3, normal: Vec3, tolerance: Double): Boolean =
    triangles.any { triangle ->
        val a = vertices[triangle.a].position
        val b = vertices[triangle.b].position
        val c = vertices[triangle.c].position
        val orientation = ((b - a) cross (c - a)) * normal
        if (abs(orientation) <= tolerance * tolerance) return@any false
        val sign = if (orientation >= 0.0) 1.0 else -1.0
        fun insideEdge(first: Vec3, second: Vec3): Boolean =
            sign * (((second - first) cross (point - first)) * normal) >=
                -tolerance * (second - first).norm
        insideEdge(a, b) && insideEdge(b, c) && insideEdge(c, a)
    }

private fun Face.sourceEdge(index: Int): Edge {
    val a = fvs[index]
    val b = fvs[(index + 1) % size]
    return directedEdges.single { edge -> edge.a == a && edge.b == b }
}

val Face.outwardNormal: Vec3
    get() = if (d >= 0.0) this else Vec3(-x, -y, -z)

private fun directedEdgeKey(edge: Edge) = edge.r.id to edge.a.id

/** Unit-offset displacement; overdetermined inputs use the three-plane solution with least residual. */
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
