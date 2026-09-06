package polyhedra.web

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.promise
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement
import polyhedra.core.api.convertStl
import polyhedra.core.poly.resolvedRims
import polyhedra.core.poly.Cube
import polyhedra.core.poly.Seed
import polyhedra.core.poly.Tetrahedron
import polyhedra.model.api.CoreStlPresentation
import polyhedra.model.api.CoreStlRequest
import polyhedra.model.poly.FaceKind
import polyhedra.model.util.MutableVec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import polyhedra.web.main.toExportFileBaseName
import polyhedra.web.glsl.get
import polyhedra.web.poly.FaceContext
import polyhedra.web.poly.FaceExportParams
import polyhedra.web.poly.RenderParams
import polyhedra.web.poly.toAsciiStl
import kotlin.math.abs
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExportStlTest {
    private val scope = MainScope()

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun usesResolvedInProposedExportFileName() {
        assertEquals("resolved_tetrahedron", "Resolved Tetrahedron".toExportFileBaseName())
        assertEquals("stellated_2_icosahedron", "Stellated 2 Icosahedron".toExportFileBaseName())
        assertEquals("greatened_2_dodecahedron", "Greatened 2 Dodecahedron".toExportFileBaseName())
        assertEquals("faceted_2_dodecahedron", "Faceted 2 Dodecahedron".toExportFileBaseName())
    }

    @Test
    fun faceContextSendsAuthoritativeGeometryAndPresentationSettings() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val poly = concavePrismFixture()
        val params = RenderParams("", null)
        params.poly.hideFaces.updateValue(setOf(FaceKind(1)))
        val faces = FaceContext(gl, params) { poly }
        try {
            faces.performUpdate(null, 0.0)
            val request = faces.buildStlRequest(
                FaceExportParams(scale = 12.0, width = 0.08, rim = 0.04, expand = 0.02),
            )
            val presentation = requireNotNull(request.presentation)
            assertSame(poly, presentation.poly)
            assertEquals(listOf(FaceKind(1)), presentation.hiddenFaceKinds)
            assertEquals(12.0, presentation.scale)
            assertEquals(0.08, presentation.width)
            assertEquals(0.04, presentation.rim)
            assertEquals(0.02, presentation.expand)
            assertTrue(request.vertices.isEmpty())
            assertTrue(request.triangles.isEmpty())
        } finally {
            faces.destroy()
        }
    }

    @Test
    fun hiddenCubeWithEqualRimAndWidthRendersSquareEdgeCrossSections() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val poly = Seed.Cube.poly
        val params = RenderParams("", null)
        params.poly.hideFaces.updateValue(poly.faceKinds.keys.toSet())
        val faces = FaceContext(gl, params) { poly }
        val width = 0.1
        val points = arrayListOf<Pair<Double, Double>>()
        try {
            faces.performUpdate(null, 0.0)
            faces.exportTriangles(FaceExportParams(1.0, width, width, 0.0)) { a, b, c ->
                val triangle = listOf(a, b, c)
                for (index in triangle.indices) {
                    val first = triangle[index]
                    val second = triangle[(index + 1) % triangle.size]
                    if (first.y * second.y > 0.0 || abs(first.y - second.y) <= 1e-12) continue
                    val fraction = -first.y / (second.y - first.y)
                    if (fraction !in 0.0..1.0) continue
                    points += (first.x + (second.x - first.x) * fraction) to
                        (first.z + (second.z - first.z) * fraction)
                }
            }
        } finally {
            faces.destroy()
        }

        val outer = poly.vs.maxOf { vertex -> vertex.x }
        val expected = listOf(
            outer to outer,
            (outer - width) to outer,
            outer to (outer - width),
            (outer - width) to (outer - width),
        )
        for (corner in expected) {
            assertTrue(
                points.any { point -> abs(point.first - corner.first) <= 5e-7 && abs(point.second - corner.second) <= 5e-7 },
                "Missing square edge-section corner $corner in $points",
            )
        }
    }

    @Test
    fun widthLargerThanConfiguredRimUsesTheBisectedInnerShell() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val poly = Seed.Cube.poly
        val width = 0.1
        val rim = 0.05
        val params = RenderParams("", null)
        params.poly.hideFaces.updateValue(poly.faceKinds.keys.toSet())
        params.poly.updateResolvedRims(poly.resolvedRims(rim, width))
        val faces = FaceContext(gl, params) { poly }
        val points = arrayListOf<Pair<Double, Double>>()
        try {
            faces.performUpdate(null, 0.0)
            faces.exportTriangles(FaceExportParams(1.0, width, rim, 0.0)) { a, b, c ->
                val triangle = listOf(a, b, c)
                for (index in triangle.indices) {
                    val first = triangle[index]
                    val second = triangle[(index + 1) % triangle.size]
                    if (first.y * second.y > 0.0 || abs(first.y - second.y) <= 1e-12) continue
                    val fraction = -first.y / (second.y - first.y)
                    if (fraction !in 0.0..1.0) continue
                    points += (first.x + (second.x - first.x) * fraction) to
                        (first.z + (second.z - first.z) * fraction)
                }
            }
        } finally {
            faces.destroy()
        }

        val outer = poly.vs.maxOf { vertex -> vertex.x }
        val expected = listOf(
            outer to outer,
            (outer - width) to outer,
            outer to (outer - width),
            (outer - width) to (outer - width),
        )
        for (corner in expected) assertTrue(
            points.any { point ->
                abs(point.first - corner.first) <= 5e-7 &&
                    abs(point.second - corner.second) <= 5e-7
            },
            "Missing bisected shell edge-section corner $corner in $points",
        )
    }

    @Test
    fun bisectedCubeAndTetrahedronRimShellsHaveConsistentSurfaceNormals() {
        for (poly in listOf(Seed.Cube.poly, Seed.Tetrahedron.poly)) {
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
            val params = RenderParams("", null)
            params.poly.hideFaces.updateValue(poly.faceKinds.keys.toSet())
            params.poly.updateResolvedRims(
                poly.resolvedRims(
                    params.view.faceRim.targetValue,
                    params.view.faceWidth.targetValue,
                ),
            )
            val faces = FaceContext(gl, params) { poly }
            try {
                faces.performUpdate(null, 0.0)
                val vertices = buildList<MutableVec3> {
                    faces.exportVertices(FaceExportParams(1.0, 0.1, 0.05, 0.0)) { point ->
                        add(MutableVec3(point))
                    }
                }
                for (offset in 0 until faces.indexSize step 3) {
                    val indices = List(3) { index -> faces.indexBuffer[offset + index] }
                    val triangle = indices.map(vertices::get)
                    val geometricNormal = triangleNormal(triangle)
                    // A rim can intentionally collapse to the inner miter line when the minimum
                    // effective rim equals the face width (the cube case). Float32 buffers leave
                    // sub-pixel sliver triangles there; they have no stable geometric normal.
                    if (geometricNormal.norm <= 1e-7) continue
                    val unitNormal = geometricNormal * (1.0 / geometricNormal.norm)
                    for (index in indices) {
                        val lightNormal = MutableVec3(
                            faces.target.lightNormalBuffer[index, 0],
                            faces.target.lightNormalBuffer[index, 1],
                            faces.target.lightNormalBuffer[index, 2],
                        )
                        assertTrue(
                            lightNormal * unitNormal >= 1.0 - 5e-6,
                            "$poly triangle $triangle has lighting normal $lightNormal instead of $unitNormal",
                        )
                    }
                }
            } finally {
                faces.destroy()
            }
        }
    }

    @Test
    fun serializesValidatedCoreGeometryAsAConsumableAsciiStl(): Promise<Unit> = scope.promise {
        val result = convertStl(
            CoreStlRequest(
                presentation = CoreStlPresentation(
                    poly = Seed.Cube.poly,
                    scale = 20.0,
                    width = 0.0,
                    rim = 0.0,
                    expand = 0.0,
                ),
            ),
        )
        assertNull(result.error, result.error?.reason)

        val validation = StlGeometryValidator.validateAscii(result.toAsciiStl("cube"))
        assertEquals(12, validation.triangleCount)
        assertTrue(validation.isValid, "Invalid ASCII STL emitted from validated core geometry: $validation")
    }
}

private fun triangleNormal(triangle: List<MutableVec3>) =
    (triangle[1] - triangle[0]) cross (triangle[2] - triangle[0])
