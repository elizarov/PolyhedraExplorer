package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import org.jetbrains.compose.web.renderComposable
import org.khronos.webgl.*
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import polyhedra.model.poly.*
import polyhedra.model.util.Vec3
import polyhedra.web.main.PrintColorPopup
import polyhedra.web.main.PrintPreviewControl
import polyhedra.web.main.RootParams
import polyhedra.web.main.HUE_POINTER_RADIUS_EM
import polyhedra.web.main.huePointerOffset
import polyhedra.web.main.plaColorPresets
import polyhedra.web.params.loadFromString
import polyhedra.web.poly.EdgeContext
import polyhedra.web.poly.FaceContext
import polyhedra.web.poly.RenderParams
import polyhedra.web.util.*
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.*

class PrintPreviewTest {
    private lateinit var host: HTMLDivElement
    private var composition: Composition? = null

    @BeforeTest
    fun setUp() {
        host = document.createElement("div") as HTMLDivElement
        document.body!!.appendChild(host)
    }

    @AfterTest
    fun tearDown() {
        composition?.dispose()
        host.parentNode?.removeChild(host)
    }

    @Test
    fun previewDefaultsToDisabledRedAndStaysImplicit() {
        val params = RootParams()
        val preview = params.render.printPreview
        val color = oklchColor(
            preview.lightness.targetValue,
            preview.chroma.targetValue,
            preview.hue.targetValue,
        )

        assertFalse(preview.enabled.value)
        assertTrue(color.r > color.g * 1.8f && color.r > color.b * 1.8f, color.toString())
        assertFalse("p(" in params.toString(), params.toString())
    }

    @Test
    fun previewAndCustomColorRoundTripThroughUrl() {
        val source = RootParams()
        source.render.printPreview.enabled.updateValue(true)
        source.render.printPreview.lightness.updateValue(0.72)
        source.render.printPreview.chroma.updateValue(0.135)
        source.render.printPreview.hue.updateValue(243.0)

        val serialized = source.toString()
        assertTrue("p(" in serialized, serialized)
        assertTrue("e(y)" in serialized, serialized)

        val restored = RootParams()
        restored.loadFromString(serialized)
        assertTrue(restored.render.printPreview.enabled.value)
        assertEquals(0.72, restored.render.printPreview.lightness.targetValue, 1e-12)
        assertEquals(0.135, restored.render.printPreview.chroma.targetValue, 1e-12)
        assertEquals(243.0, restored.render.printPreview.hue.targetValue, 1e-12)
    }

    @Test
    fun exportPreviewRowEnablesLivePreviewBeforeOpeningPicker() {
        val preview = RootParams().render.printPreview
        var opens = 0
        composition = renderComposable(host) { PrintPreviewControl(preview) { opens++ } }

        assertNotNull(host.querySelector("input[type=checkbox]") as? HTMLInputElement)
        assertNotNull(host.querySelector(".print-color-sample"))
        val picker = assertNotNull(host.querySelector("button.pick-print-color") as? HTMLButtonElement)
        assertEquals("Pick color", picker.textContent)

        picker.click()
        assertTrue(preview.enabled.value)
        assertEquals(1, opens)
    }

    @Test
    fun pickerOffersPlaPresetsPerceptualControlsAndBackNavigation() {
        val preview = RootParams().render.printPreview
        var backs = 0
        composition = renderComposable(host) { PrintColorPopup(preview) { backs++ } }

        assertEquals(plaColorPresets.size, host.querySelectorAll("button.pla-color-preset").length)
        assertEquals(3, host.querySelectorAll("input[type=range]").length)
        assertNotNull(host.querySelector("button.hue-wheel"))
        assertNotNull(host.querySelector(".print-color-components"))
        assertTrue(host.textContent.orEmpty().contains("Basic colors"))
        assertFalse(host.textContent.orEmpty().contains("Typical PLA colors"))
        assertTrue(host.textContent.orEmpty().contains("Custom color · OKLCH"))

        val blue = assertNotNull(host.querySelector("button[title=Blue]") as? HTMLButtonElement)
        blue.click()
        val chosen = oklchColor(
            preview.lightness.targetValue,
            preview.chroma.targetValue,
            preview.hue.targetValue,
        )
        assertTrue(chosen.b > chosen.r && chosen.b > chosen.g, chosen.toString())

        (host.querySelector("button.print-color-back") as HTMLButtonElement).click()
        assertEquals(1, backs)
    }

    @Test
    fun oklchRoundTripsRepresentativePlaColorsAndMapsExtremeChromaToSrgb() {
        for (hex in listOf("#D93632", "#36A85B", "#2878C7", "#F4F1E8", "#1E2022")) {
            val original = colorFromHex(hex)
            val restored = original.toOklch().toColor()
            assertTrue(abs(original.r - restored.r) < 0.002, "$hex red: $restored")
            assertTrue(abs(original.g - restored.g) < 0.002, "$hex green: $restored")
            assertTrue(abs(original.b - restored.b) < 0.002, "$hex blue: $restored")
        }

        val mapped = oklchColor(0.92, 0.30, 130.0)
        assertTrue(mapped.r in 0.0f..1.0f)
        assertTrue(mapped.g in 0.0f..1.0f)
        assertTrue(mapped.b in 0.0f..1.0f)
    }

    @Test
    fun huePointerAlwaysRotatesAroundTheExactWheelCenter() {
        for (hue in listOf(0.0, 90.0, 180.0, 252.0, 270.0, 359.0)) {
            val (x, y) = huePointerOffset(hue)
            assertEquals(HUE_POINTER_RADIUS_EM, hypot(x, y), 1e-12, "Hue $hue")
        }
        val blue = huePointerOffset(252.0)
        assertTrue(blue.first < 0.0 && blue.second < 0.0)
    }

    @Test
    fun faceBuffersUseOnePrintColorAcrossAllFaceOrbits() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = assertNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val params = RenderParams("", null)
        val faces = FaceContext(gl, params) { twoColorCube() }
        try {
            faces.performUpdate(null, 0.0)
            val normalColors = bufferColors(faces)
            assertTrue(normalColors.size > 1, "Test fixture must render multiple orbit colors")

            params.printPreview.enabled.updateValue(true)
            params.printPreview.lightness.updateValue(0.70)
            params.printPreview.chroma.updateValue(0.12)
            params.printPreview.hue.updateValue(220.0)
            faces.performUpdate(null, 0.0)

            val expected = oklchColor(0.70, 0.12, 220.0)
            val colors = bufferColors(faces)
            assertEquals(1, colors.size)
            val actual = colors.single()
            assertEquals(expected.r, actual.first, 1e-6f)
            assertEquals(expected.g, actual.second, 1e-6f)
            assertEquals(expected.b, actual.third, 1e-6f)
        } finally {
            faces.destroy()
        }
    }

    @Test
    fun previewSuppressesNormalAndSelectedEdgePasses() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = assertNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val params = RenderParams("", null)
        val poly = twoColorCube()
        val edges = EdgeContext(gl, params) { poly }
        try {
            params.poly.selectedEdge.updateValue(poly.edgeKinds.keys.first())
            edges.performUpdate(null, 0.0)
            assertTrue(edges.drawEdges)
            assertTrue(edges.indexSize > 0)
            assertTrue(edges.selectedIndexSize > 0)

            params.printPreview.enabled.updateValue(true)
            edges.performUpdate(null, 0.0)
            assertFalse(edges.drawEdges)
            assertEquals(0, edges.indexSize)
            assertEquals(0, edges.selectedIndexSize)
        } finally {
            edges.destroy()
        }
    }

    private fun bufferColors(faces: FaceContext): Set<Triple<Float, Float, Float>> = buildSet {
        val data = faces.target.colorBuffer.data
        for (offset in 0 until faces.bufferSize) {
            val index = offset * 3
            add(Triple(data[index], data[index + 1], data[index + 2]))
        }
    }

    private fun twoColorCube(): Polyhedron {
        val vertices = listOf(
            Vec3(1.0, 1.0, -1.0), Vec3(-1.0, 1.0, -1.0),
            Vec3(-1.0, -1.0, -1.0), Vec3(1.0, -1.0, -1.0),
            Vec3(1.0, 1.0, 1.0), Vec3(-1.0, 1.0, 1.0),
            Vec3(-1.0, -1.0, 1.0), Vec3(1.0, -1.0, 1.0),
        ).mapIndexed { index, point -> MutableVertex(index, point, VertexKind(0)) }
        val faceVertexIds = listOf(
            listOf(0, 1, 2, 3), listOf(0, 4, 5, 1), listOf(1, 5, 6, 2),
            listOf(2, 6, 7, 3), listOf(3, 7, 4, 0), listOf(4, 7, 6, 5),
        )
        val faces = faceVertexIds.mapIndexed { index, ids ->
            MutableFace(index, ids.map(vertices::get), FaceKind(index % 2))
        }
        return Polyhedron(vertices, faces, faceKindSources = null)
    }
}
