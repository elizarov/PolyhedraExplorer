package polyhedra.web

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.promise
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement
import polyhedra.core.api.evaluateCore
import polyhedra.model.poly.FaceKind
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.web.poly.*
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportStlTest {
    private val scope = MainScope()

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun exportsReportedHiddenFaceConfigurationAsValidGeometry(): Promise<Unit> = scope.promise {
        // Geometry-affecting state from:
        // #/a(r(n)rs(0.45)ra(332))s(I)t(e,d,t)hf(δ,β,α,γ)v(r(-122.2,-16.8,-46.9))
        val response = evaluateCore(
            CoreRequest(CoreState("I", listOf("e", "d", "t"), "c")),
        )
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val params = RenderParams("", null)
        params.poly.hideFaces.updateValue((0..3).mapTo(linkedSetOf(), ::FaceKind))
        val faces = FaceContext(gl, params) { response.poly }
        try {
            faces.performUpdate(null, 0.0)
            val stl = faces.exportSolidToStl(
                "truncated_dual_cantellated_icosahedron",
                FaceExportParams(scale = 20.0, width = 0.1, rim = 0.05, expand = 0.0),
            )
            val validation = StlGeometryValidator.validateAscii(stl)
            // Robust rim limits keep every generated surface triangle non-degenerate.
            assertEquals(4_320, validation.triangleCount)
            assertTrue(validation.isValid, "Invalid STL geometry for the reported configuration: $validation")
        } finally {
            faces.destroy()
        }
    }

    @Test
    fun exportsConcaveFacesAsWatertightEarClippedGeometry() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val params = RenderParams("", null)
        val poly = concavePrismFixture()
        val faces = FaceContext(gl, params) { poly }
        try {
            faces.performUpdate(null, 0.0)
            val stl = faces.exportSolidToStl(
                "concave_prism",
                FaceExportParams(scale = 10.0, width = 0.0, rim = 0.0, expand = 0.0),
            )
            val validation = StlGeometryValidator.validateAscii(stl)
            assertEquals(28, validation.triangleCount)
            assertTrue(validation.isValid, "Invalid STL geometry for a concave face: $validation")
        } finally {
            faces.destroy()
        }
    }
}
