package polyhedra.core.poly

import polyhedra.model.poly.FEV
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.fev
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.div
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.plus
import polyhedra.model.util.times
import polyhedra.model.util.unit
import polyhedra.model.util.unaryMinus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Physical, embedded interpretations of the four Kepler-Poinsot polyhedra.
 *
 * Their classical regular faces form an immersed, self-intersecting surface. The project does not
 * admit such meshes: rendering and export require a connected embedded two-manifold. We therefore
 * partition every classical face at all face-plane intersections and retain the boundary between
 * zero and non-zero winding regions. This is the same physical-surface interpretation used when a
 * paper or printable model is split along all visible face intersections.
 */
internal object KeplerPoinsotGeometry {
    val smallStellatedDodecahedron: Polyhedron by lazy {
        resolveStarSurface(
            regularPentagonFaces(icosahedronGeometry.vs, star = true),
            "small stellated dodecahedron",
        )
    }

    val greatDodecahedron: Polyhedron by lazy {
        resolveStarSurface(
            regularPentagonFaces(icosahedronGeometry.vs, star = false),
            "great dodecahedron",
        )
    }

    val greatStellatedDodecahedron: Polyhedron by lazy {
        resolveStarSurface(
            regularPentagonFaces(dodecahedronGeometry.vs, star = true, innerPlanes = true),
            "great stellated dodecahedron",
        )
    }

    val greatIcosahedron: Polyhedron by lazy {
        resolveStarSurface(
            greatIcosahedronFaces(icosahedronGeometry.vs),
            "great icosahedron",
        )
    }
}

internal enum class RegularStarForm {
    Dodecahedron,
    Icosahedron,
    SmallStellatedDodecahedron,
    GreatDodecahedron,
    GreatStellatedDodecahedron,
    GreatIcosahedron,
}

/** Recognizes only the regular geometry, independent of scale and vertex numbering. */
internal fun Polyhedron.regularStarFormOrNull(): RegularStarForm? {
    val candidates = when (fev()) {
        FEV(12, 30, 20) -> listOf(RegularStarForm.Dodecahedron to dodecahedronGeometry)
        FEV(20, 30, 12) -> listOf(RegularStarForm.Icosahedron to icosahedronGeometry)
        FEV(60, 90, 32) -> listOf(
            RegularStarForm.SmallStellatedDodecahedron to KeplerPoinsotGeometry.smallStellatedDodecahedron,
            RegularStarForm.GreatDodecahedron to KeplerPoinsotGeometry.greatDodecahedron,
            RegularStarForm.GreatStellatedDodecahedron to KeplerPoinsotGeometry.greatStellatedDodecahedron,
        )
        FEV(180, 270, 92) -> listOf(
            RegularStarForm.GreatIcosahedron to KeplerPoinsotGeometry.greatIcosahedron,
        )
        else -> return null
    }
    val fingerprint = geometryFingerprint()
    return candidates.firstOrNull { (_, template) ->
        fingerprint.matches(template.geometryFingerprint())
    }?.first
}

private const val STAR_TOLERANCE = 1e-7
private const val SAMPLE_OFFSET = 2e-6

private data class StarPlane(val normal: Vec3, val distance: Double)

private data class StarFace(
    val vertices: List<Vec3>,
    val plane: StarPlane,
    val basisU: Vec3,
    val basisV: Vec3,
) {
    fun windingAt(point: Vec3): Int {
        val px = point * basisU
        val py = point * basisV
        var winding = 0
        for (index in vertices.indices) {
            val a = vertices[index]
            val b = vertices[(index + 1) % vertices.size]
            val ax = a * basisU
            val ay = a * basisV
            val bx = b * basisU
            val by = b * basisV
            val side = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
            when {
                ay <= py && py < by && side > STAR_TOLERANCE -> winding++
                by <= py && py < ay && side < -STAR_TOLERANCE -> winding--
            }
        }
        return winding
    }
}

private data class BoundaryTriangle(
    val a: Vec3,
    val b: Vec3,
    val c: Vec3,
)

private fun resolveStarSurface(rawFaces: List<List<Vec3>>, name: String): Polyhedron {
    val faces = rawFaces.map(::starFace)
    val radius = rawFaces.flatten().maxOf(Vec3::norm)
    val tolerance = STAR_TOLERANCE * radius.coerceAtLeast(1.0)
    val ray = chooseWindingRay(faces)
    val candidates = ArrayList<Pair<BoundaryTriangle, Vec3>>()

    for (face in faces) {
        var cells = listOf(convexOrder(face.vertices, face.plane.normal))
        for (cut in faces.map(StarFace::plane)) {
            if ((face.plane.normal cross cut.normal).norm <= tolerance) continue
            cells = cells.flatMap { polygon -> splitPolygon(polygon, cut, tolerance) }
        }
        for (cell in cells) {
            if (face.windingAt(centroid(cell)) == 0) continue
            for (index in 1 until cell.lastIndex) {
                val triangle = BoundaryTriangle(cell[0], cell[index], cell[index + 1])
                if (((triangle.b - triangle.a) cross (triangle.c - triangle.a)).norm > tolerance * tolerance) {
                    candidates += triangle to face.plane.normal
                }
            }
        }
    }

    val sampleDistance = SAMPLE_OFFSET * radius.coerceAtLeast(1.0)
    val boundary = candidates.mapNotNull { (triangle, normal) ->
        val center = centroid(listOf(triangle.a, triangle.b, triangle.c))
        val minusWinding = windingAt(center - normal * sampleDistance, faces, ray, tolerance)
        val plusWinding = windingAt(center + normal * sampleDistance, faces, ray, tolerance)
        val minusInside = minusWinding != 0
        val plusInside = plusWinding != 0
        when {
            minusInside == plusInside -> null
            // Polyhedron faces are clockwise when viewed from outside.
            minusInside -> BoundaryTriangle(triangle.a, triangle.c, triangle.b)
            else -> triangle
        }
    }

    require(boundary.isNotEmpty()) { "The $name has no resolved physical boundary" }
    val vertexTolerance = tolerance * 10.0
    val positions = ArrayList<Vec3>()
    fun vertexIndex(point: Vec3): Int {
        val existing = positions.indexOfFirst { candidate -> (candidate - point).norm <= vertexTolerance }
        if (existing >= 0) return existing
        positions += point
        return positions.lastIndex
    }
    val triangleIndices = boundary.map { triangle ->
        listOf(vertexIndex(triangle.a), vertexIndex(triangle.b), vertexIndex(triangle.c))
    }.distinct()

    val poly = polyhedron(mergeIndistinguishableKinds = true) {
        positions.forEachIndexed { index, point -> vertex(point, VertexKind(index)) }
        triangleIndices.forEachIndexed { index, triangle -> face(triangle, FaceKind(index)) }
    }
    poly.validateProperGeometry()
    return poly
}

private fun starFace(points: List<Vec3>): StarFace {
    var vertices = points
    var normal = polygonNormal(vertices)
    if (normal * centroid(vertices) < 0.0) {
        vertices = vertices.asReversed()
        normal = -normal
    }
    val (basisU, basisV) = planeBasis(normal)
    return StarFace(vertices, StarPlane(normal, normal * vertices.first()), basisU, basisV)
}

private fun polygonNormal(points: List<Vec3>): Vec3 {
    val center = centroid(points)
    var sum: Vec3 = Vec3.ZERO
    for (index in points.indices) {
        sum += (points[index] - center) cross (points[(index + 1) % points.size] - center)
    }
    require(sum.norm > STAR_TOLERANCE) { "Star face has no well-defined plane" }
    return sum.unit
}

private fun planeBasis(normal: Vec3): Pair<Vec3, Vec3> {
    val axis = if (abs(normal.x) < 0.8) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
    val basisU = (axis cross normal).unit
    return basisU to (normal cross basisU)
}

private fun convexOrder(points: List<Vec3>, outwardNormal: Vec3): List<Vec3> {
    val center = centroid(points)
    val (basisU, basisV) = planeBasis(outwardNormal)
    val ordered = points.sortedBy { point ->
        val relative = point - center
        atan2(relative * basisV, relative * basisU)
    }
    return if (
        ((ordered[1] - ordered[0]) cross (ordered[2] - ordered[1])) * outwardNormal > 0.0
    ) ordered else ordered.asReversed()
}

private fun splitPolygon(polygon: List<Vec3>, plane: StarPlane, tolerance: Double): List<List<Vec3>> {
    val distances = polygon.map { point -> plane.normal * point - plane.distance }
    if (distances.none { it > tolerance } || distances.none { it < -tolerance }) return listOf(polygon)

    fun clipped(positive: Boolean): List<Vec3> {
        val result = ArrayList<Vec3>()
        for (index in polygon.indices) {
            val a = polygon[index]
            val b = polygon[(index + 1) % polygon.size]
            val da = distances[index]
            val db = distances[(index + 1) % polygon.size]
            val aInside = if (positive) da >= -tolerance else da <= tolerance
            val bInside = if (positive) db >= -tolerance else db <= tolerance
            if (aInside) result += a
            if (aInside != bInside && abs(da - db) > tolerance) {
                result += a + (b - a) * (da / (da - db))
            }
        }
        return cleanPolygon(result, tolerance)
    }

    return listOf(clipped(true), clipped(false)).filter { it.size >= 3 }
}

private fun cleanPolygon(points: List<Vec3>, tolerance: Double): List<Vec3> {
    val unique = ArrayList<Vec3>()
    for (point in points) {
        if (unique.isEmpty() || (unique.last() - point).norm > tolerance) unique += point
    }
    if (unique.size > 1 && (unique.first() - unique.last()).norm <= tolerance) unique.removeLast()
    var changed: Boolean
    do {
        changed = false
        if (unique.size < 3) break
        for (index in unique.indices) {
            val previous = unique[(index + unique.size - 1) % unique.size]
            val current = unique[index]
            val next = unique[(index + 1) % unique.size]
            val first = current - previous
            val second = next - current
            if ((first cross second).norm <= tolerance * maxOf(first.norm, second.norm, 1.0)) {
                unique.removeAt(index)
                changed = true
                break
            }
        }
    } while (changed)
    return unique
}

private fun chooseWindingRay(faces: List<StarFace>): Vec3 {
    val candidates = listOf(
        Vec3(1.0, sqrt(2.0), PI),
        Vec3(sqrt(3.0), 1.0, sqrt(5.0)),
        Vec3(PI, sqrt(7.0), 1.0),
    ).map(Vec3::unit)
    return candidates.maxBy { ray -> faces.minOf { face -> abs(face.plane.normal * ray) } }
}

private fun windingAt(
    point: Vec3,
    faces: List<StarFace>,
    ray: Vec3,
    tolerance: Double,
): Int {
    var winding = 0
    for (face in faces) {
        val direction = face.plane.normal * ray
        if (abs(direction) <= tolerance) continue
        val distance = (face.plane.distance - face.plane.normal * point) / direction
        if (distance <= tolerance) continue
        val faceWinding = face.windingAt(point + ray * distance)
        winding += if (direction > 0.0) faceWinding else -faceWinding
    }
    return winding
}

private fun regularPentagonFaces(
    vertices: List<Vec3>,
    star: Boolean,
    innerPlanes: Boolean = false,
): List<List<Vec3>> {
    val tolerance = STAR_TOLERANCE * vertices.maxOf(Vec3::norm).coerceAtLeast(1.0)
    val groups = LinkedHashMap<List<Int>, Double>()
    for (a in 0 until vertices.size - 2) {
        for (b in a + 1 until vertices.size - 1) {
            for (c in b + 1 until vertices.size) {
                val rawNormal = (vertices[b] - vertices[a]) cross (vertices[c] - vertices[a])
                if (rawNormal.norm <= tolerance) continue
                val normal = rawNormal.unit
                val distance = normal * vertices[a]
                val group = vertices.indices.filter { index ->
                    abs(normal * vertices[index] - distance) <= tolerance
                }
                if (group.size == 5) groups[group.sorted()] = abs(distance)
            }
        }
    }
    require(groups.isNotEmpty()) { "No regular pentagon planes were found" }
    val selected = if (innerPlanes) {
        val minimumDistance = groups.values.min()
        groups.filterValues { distance -> abs(distance - minimumDistance) <= tolerance }.keys
    } else {
        groups.keys
    }
    require(selected.size == 12) { "Expected 12 regular pentagon planes, found ${selected.size}" }
    return selected.map { group ->
        val convex = convexOrder(group.map(vertices::get), outwardNormal(group.map(vertices::get)))
        if (star) List(5) { index -> convex[(2 * index) % 5] } else convex
    }
}

private fun outwardNormal(points: List<Vec3>): Vec3 {
    val center = centroid(points)
    for (a in 0 until points.size - 2) {
        for (b in a + 1 until points.size - 1) {
            for (c in b + 1 until points.size) {
                val raw = (points[b] - points[a]) cross (points[c] - points[a])
                if (raw.norm <= STAR_TOLERANCE) continue
                val normal = raw.unit
                return if (normal * center >= 0.0) normal else -normal
            }
        }
    }
    error("Cannot determine a face normal")
}

private fun greatIcosahedronFaces(vertices: List<Vec3>): List<List<Vec3>> {
    val tolerance = STAR_TOLERANCE * vertices.maxOf(Vec3::norm).coerceAtLeast(1.0)
    val distances = buildList {
        for (a in 0 until vertices.lastIndex) {
            for (b in a + 1 until vertices.size) add((vertices[a] - vertices[b]).norm)
        }
    }.sorted().fold(ArrayList<Double>()) { distinct, distance ->
        if (distinct.isEmpty() || abs(distinct.last() - distance) > tolerance) distinct += distance
        distinct
    }
    require(distances.size >= 2)
    val edgeLength = distances[1]
    val faces = ArrayList<List<Vec3>>()
    for (a in 0 until vertices.size - 2) {
        for (b in a + 1 until vertices.size - 1) {
            for (c in b + 1 until vertices.size) {
                val points = listOf(vertices[a], vertices[b], vertices[c])
                val lengths = listOf(
                    (points[0] - points[1]).norm,
                    (points[1] - points[2]).norm,
                    (points[2] - points[0]).norm,
                )
                if (lengths.all { length -> abs(length - edgeLength) <= tolerance }) {
                    faces += if (
                        ((points[1] - points[0]) cross (points[2] - points[0])) * centroid(points) > 0.0
                    ) points else listOf(points[0], points[2], points[1])
                }
            }
        }
    }
    require(faces.size == 20) { "Expected 20 great-icosahedron faces, found ${faces.size}" }
    return faces
}

private fun centroid(points: List<Vec3>): Vec3 {
    var center: Vec3 = Vec3.ZERO
    for (point in points) center += point
    return center / points.size.toDouble()
}
