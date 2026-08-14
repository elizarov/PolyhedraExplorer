package polyhedra.web

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.promise
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement
import polyhedra.core.api.convertStl
import polyhedra.core.poly.Cube
import polyhedra.core.poly.Seed
import polyhedra.model.api.CoreStlPresentation
import polyhedra.model.api.CoreStlRequest
import polyhedra.model.poly.FaceKind
import polyhedra.web.main.toExportFileBaseName
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
