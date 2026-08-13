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
