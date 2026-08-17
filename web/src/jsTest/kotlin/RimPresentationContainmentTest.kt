package polyhedra.web

import kotlinx.browser.document
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement
import polyhedra.core.poly.resolvedRims
import polyhedra.core.poly.toSeedOrNull
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.plus
import polyhedra.model.util.times
import polyhedra.web.params.Param
import polyhedra.web.poly.FaceContext
import polyhedra.web.poly.FaceExportParams
import polyhedra.web.poly.RenderParams
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The rendered rim presentation must remain a subset of the complete resolved solid.
 * High-winding cases with intentional internal sheets use the independent local tests instead.
 */
class RimPresentationContainmentTest {
    @Test
    fun convexRimPresentationsStayInsideTheirResolvedSolids() {
        val settings = listOf(
            0.015 to 0.015,
            0.015 to 0.1,
            0.1 to 0.05,
            0.12 to 0.12,
        )
        for (tag in listOf("C", "T", "P3")) for ((rim, width) in settings) {
            assertRimPresentationContained(tag, rim, width)
        }
    }

    @Test
    fun starPrismRimPresentationStaysInsideItsResolvedSolid() {
        for ((rim, width) in challengingSettings) {
            assertRimPresentationContained("SP5_2", rim, width)
        }
    }

    @Test
    fun starPrismMixedFacePresentationsStayInsideItsResolvedSolid() {
        for ((rim, width) in listOf(0.015 to 0.1, 0.12 to 0.112)) {
            assertRimPresentationContained("SP5_2", rim, width, setOf(FaceKind(0)))
            assertRimPresentationContained("SP5_2", rim, width, setOf(FaceKind(1)))
        }
    }

    @Test
    fun sevenThirdsStarPrismRimsStayInsideItsResolvedSolid() {
        for ((rim, width) in challengingSettings) {
            assertRimPresentationContained("SP7_3", rim, width)
        }
    }

    @Test
    fun fiveHalvesStarPyramidRimsStayInsideItsResolvedSolid() {
        for ((rim, width) in challengingSettings) {
            assertRimPresentationContained("SY5_2", rim, width)
        }
    }

    @Test
    fun sevenThirdsStarAntiprismRimsStayInsideItsResolvedSolid() {
        for ((rim, width) in challengingSettings) {
            assertRimPresentationContained("SA7_3", rim, width)
        }
    }

    @Test
    fun stellatedDodecahedronRimsStayInsideItsResolvedSolid() {
        for ((rim, width) in challengingSettings + (0.015 to 0.129)) {
            assertRimPresentationContained("SD", rim, width)
        }
    }

    private val challengingSettings = listOf(
        0.015 to 0.03,
        0.015 to 0.1,
        0.08 to 0.03,
        0.1 to 0.1,
    )

    private fun assertRimPresentationContained(
        tag: String,
        rim: Double,
        width: Double,
        hiddenKinds: Set<FaceKind>? = null,
    ) {
        val poly = requireNotNull(tag.toSeedOrNull()).poly
        val rendered = poly.renderedRimTriangles(rim, width, hiddenKinds ?: poly.faceKinds.keys)
        val solid = ResolvedSolid(poly)
        for ((triangleIndex, triangle) in rendered.withIndex()) {
            solid.firstOutsidePoint(triangle)?.let { point ->
                assertTrue(
                    false,
                    "$tag rim=$rim width=$width rim triangle $triangleIndex protrudes outside " +
                        "its complete resolved solid " +
                        "at $point; triangle=${triangle.vertices}",
                )
            }
        }
    }

    private fun Polyhedron.renderedRimTriangles(
        rim: Double,
        width: Double,
        hiddenKinds: Set<FaceKind>,
    ): List<TestTriangle> {
        val params = RenderParams("", null)
        params.poly.hideFaces.updateValue(hiddenKinds)
        params.view.faceRim.updateUnsnappedValue(rim, Param.TargetValue)
        params.view.faceWidth.updateUnsnappedValue(width, Param.TargetValue)
        params.poly.updateResolvedRims(resolvedRims(rim, width))
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val context = FaceContext(gl, params) { this }
        return try {
            context.performUpdate(null, 0.0)
            buildList {
                context.exportTriangles(FaceExportParams(1.0, width, rim, 0.0)) { a, b, c ->
                    add(TestTriangle(a.copy(), b.copy(), c.copy()))
                }
            }
        } finally {
            context.destroy()
        }
    }
}

private data class TestTriangle(val a: Vec3, val b: Vec3, val c: Vec3, val multiplicity: Int = 1) {
    val vertices = listOf(a, b, c)
    val normalVector = (b - a) cross (c - a)
    val normal = normalVector * (1.0 / normalVector.norm)
    val edgeScale = maxOf((b - a).norm, (c - b).norm, (a - c).norm)
    val minX = minOf(a.x, b.x, c.x)
    val maxX = maxOf(a.x, b.x, c.x)
    val minY = minOf(a.y, b.y, c.y)
    val maxY = maxOf(a.y, b.y, c.y)
    val minZ = minOf(a.z, b.z, c.z)
    val maxZ = maxOf(a.z, b.z, c.z)

    fun containsInPlane(point: Vec3, tolerance: Double): Boolean {
        if (abs((point - a) * normal) > tolerance) return false
        fun insideEdge(first: Vec3, second: Vec3): Boolean =
            (((second - first) cross (point - first)) * normal) >= -tolerance * (second - first).norm
        return insideEdge(a, b) && insideEdge(b, c) && insideEdge(c, a)
    }

    fun boundsOverlap(other: TestTriangle, tolerance: Double): Boolean =
        minX <= other.maxX + tolerance && other.minX <= maxX + tolerance &&
            minY <= other.maxY + tolerance && other.minY <= maxY + tolerance &&
            minZ <= other.maxZ + tolerance && other.minZ <= maxZ + tolerance
}

private class ResolvedSolid(poly: Polyhedron) {
    private val tolerance = maxOf(poly.circumradius, 1.0) * 2e-7
    private val surface = poly.fs.flatMap { face ->
        val resolved = poly.resolvedFaces[face.id]
        resolved.cells.flatMap { cell ->
            cell.triangles.map { triangle ->
                TestTriangle(
                    resolved.vertices[triangle.a].position,
                    resolved.vertices[triangle.b].position,
                    resolved.vertices[triangle.c].position,
                    cell.winding,
                )
            }
        }
    }

    fun firstOutsidePoint(triangle: TestTriangle): Vec3? {
        val samples = listOf(
            triangle.a,
            triangle.b,
            triangle.c,
            (triangle.a + triangle.b) * 0.5,
            (triangle.b + triangle.c) * 0.5,
            (triangle.c + triangle.a) * 0.5,
            (triangle.a + triangle.b + triangle.c) * (1.0 / 3.0),
        )
        samples.firstOrNull { point -> !containsOrOnBoundary(point) }?.let { return it }

        for (boundary in surface) {
            if (!triangle.boundsOverlap(boundary, tolerance)) continue
            val intersection = triangle.intersectionSegment(boundary, tolerance) ?: continue
            val midpoint = (intersection.first + intersection.second) * 0.5
            val along = (intersection.second - intersection.first)
            if (along.norm <= tolerance) continue
            val across = (along cross triangle.normal).let { direction -> direction * (1.0 / direction.norm) }
            for (distance in listOf(tolerance * 8.0, triangle.edgeScale * 1e-5)) {
                for (sign in listOf(-1.0, 1.0)) {
                    val sample = midpoint + across * (distance * sign)
                    if (triangle.containsInPlane(sample, tolerance) && !containsOrOnBoundary(sample)) {
                        return sample
                    }
                }
            }
        }
        return null
    }

    private fun containsOrOnBoundary(point: Vec3): Boolean {
        if (surface.any { triangle -> triangle.containsInPlane(point, tolerance) }) return true
        var solidAngle = 0.0
        for (triangle in surface) {
            val a = triangle.a - point
            val b = triangle.b - point
            val c = triangle.c - point
            val la = a.norm
            val lb = b.norm
            val lc = c.norm
            if (minOf(la, lb, lc) <= tolerance) return true
            val numerator = a * (b cross c)
            val denominator = la * lb * lc + (a * b) * lc + (b * c) * la + (c * a) * lb
            solidAngle += triangle.multiplicity * 2.0 * atan2(numerator, denominator)
        }
        return abs(solidAngle / (4.0 * PI)) > 0.5
    }
}

private fun TestTriangle.intersectionSegment(other: TestTriangle, tolerance: Double): Pair<Vec3, Vec3>? {
    val line = normal cross other.normal
    if (line.norm <= tolerance) return null
    val direction = line * (1.0 / line.norm)
    val first = sectionByPlane(other.normal, other.normal * other.a, tolerance) ?: return null
    val second = other.sectionByPlane(normal, normal * a, tolerance) ?: return null
    val origin = first.first
    fun Pair<Vec3, Vec3>.range(): Pair<Double, Double> {
        val firstValue = (this.first - origin) * direction
        val secondValue = (this.second - origin) * direction
        return minOf(firstValue, secondValue) to maxOf(firstValue, secondValue)
    }
    val firstRange = first.range()
    val secondRange = second.range()
    val start = maxOf(firstRange.first, secondRange.first)
    val end = minOf(firstRange.second, secondRange.second)
    if (end - start <= tolerance) return null
    return origin + direction * start to origin + direction * end
}

private fun TestTriangle.sectionByPlane(
    planeNormal: Vec3,
    planeDistance: Double,
    tolerance: Double,
): Pair<Vec3, Vec3>? {
    val points = arrayListOf<Vec3>()
    for (index in vertices.indices) {
        val first = vertices[index]
        val second = vertices[(index + 1) % vertices.size]
        val firstDistance = planeNormal * first - planeDistance
        val secondDistance = planeNormal * second - planeDistance
        if (abs(firstDistance) <= tolerance) points += first
        if (firstDistance * secondDistance < -tolerance * tolerance) {
            val fraction = firstDistance / (firstDistance - secondDistance)
            points += first + (second - first) * fraction
        }
    }
    val distinct = points.distinctBy { point ->
        listOf(
            (point.x / tolerance).toLong(),
            (point.y / tolerance).toLong(),
            (point.z / tolerance).toLong(),
        )
    }
    if (distinct.size < 2) return null
    var best = distinct[0] to distinct[1]
    var bestDistance = (best.second - best.first).norm
    for (first in distinct.indices) for (second in first + 1 until distinct.size) {
        val distance = (distinct[second] - distinct[first]).norm
        if (distance > bestDistance) {
            best = distinct[first] to distinct[second]
            bestDistance = distance
        }
    }
    return best.takeIf { bestDistance > tolerance }
}

private fun Vec3.copy() = Vec3(x, y, z)
