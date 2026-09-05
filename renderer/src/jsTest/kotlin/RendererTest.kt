package polyhedra.renderer

import kotlinx.coroutines.test.runTest
import org.khronos.webgl.WebGLRenderingContext as GL
import polyhedra.core.api.inspectCompactConfiguration
import polyhedra.web.main.RootParams
import polyhedra.web.params.Param
import polyhedra.web.params.loadFromString
import polyhedra.web.poly.DrawContext
import polyhedra.web.poly.drawScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

class RendererTest {
    @Test
    fun cutFacesRetainsAFaintShellWireframeUnlessFacesOnlyOrPrintPreview() = runTest {
        // At -1 the entire cube is removed, isolating the ghost from retained faces and edges.
        fun configuration(view: String, preview: String = "") = "a(r(n))s(C)v(env(n)$view)$preview"
        val ghost = renderConfiguration(configuration("c(y)cp(-1)"), 320, 240)
        val facesOnly = renderConfiguration(configuration("c(y)cp(-1)d(f)"), 320, 240)
        val printPreview = renderConfiguration(configuration("c(y)cp(-1)", "p(e(y))"), 320, 240)
        val opaqueEdges = renderConfiguration(configuration("d(e)"), 320, 240)
        val transparentGhost = renderConfiguration(configuration("c(y)cp(-1)t(0.3)"), 320, 240)
        assertTrue(nonBackgroundPixels(ghost) > 100, "Removed faces must leave the shell wireframe")
        assertEquals(0, nonBackgroundPixels(facesOnly), "Faces-only must hide the ghost")
        assertEquals(0, nonBackgroundPixels(printPreview), "Print preview must still suppress edges")
        assertEquals(0, changedPixels(ghost, transparentGhost), "Transparency must not draw ghost edges twice")
        val ghostInk = (0 until ghost.rgba.length step 4).sumOf { 242 - ghost.rgba.unsigned(it) }
        val opaqueInk = (0 until opaqueEdges.rgba.length step 4).sumOf { 242 - opaqueEdges.rgba.unsigned(it) }
        assertTrue(ghostInk in 1 until opaqueInk / 2, "Removed edges should be faint, not solid black")
    }

    @Test
    fun rotatingCutawayUpdatesLightingWithoutReuploadingGeometry() = runTest {
        for (seed in listOf("C", "SA5_2")) {
            val inspection = inspectCompactConfiguration(
                "a(r(n))s($seed)v(c(y)e(0.12))",
                calculateTweakRanges = false,
                detectSeed = false,
            )
            val params = RootParams()
            params.loadFromString(inspection.configuration.normalized)
            params.render.poly.applyCoreResponse(inspection.configuration.state, inspection.response)
            val gl = createContext(480, 360)
            val draw = DrawContext(gl, params.render) {}
            try {
                params.performUpdate(null, 0.0)
                draw.drawScene(480, 360)
                gl.finish()
                var uploads = 0
                val originalBufferData = gl.asDynamic().bufferData
                gl.asDynamic().bufferData = { target: Int, data: dynamic, usage: Int ->
                    uploads++
                    originalBufferData.call(gl, target, data, usage)
                }
                val elapsed = measureTime {
                    repeat(24) { frame ->
                        with(params.render.view) {
                            rotate.rotate(0.03, 0.05, 0.01, Param.TargetValue)
                            cutPosition.updateValue(-0.5 + frame / 24.0, Param.TargetValue)
                            scale.updateValue(frame / 48.0, Param.TargetValue)
                            transparentFaces.updateValue(if (frame % 2 == 0) 0.0 else 0.3, Param.TargetValue)
                        }
                        params.performUpdate(null, 0.0)
                        draw.drawScene(480, 360)
                        gl.finish()
                        assertEquals(GL.NO_ERROR, gl.getError(), "$seed frame $frame")
                        assertTrue(gl.isEnabled(GL.CULL_FACE), "Cut must restore culling after drawing")
                        val radius = gl.getUniform(draw.faces.program.program, draw.faces.program.uInteriorRadius.location) as Double
                        val expected = (draw.faces.poly.circumradius + draw.view.expandFaces) * draw.view.scaleFactor
                        assertEquals(expected, radius, 1e-5, "Interior scale must update on every frame")
                    }
                }
                assertEquals(0, uploads, "Rotation, cut, zoom and transparency must not rebuild face buffers")
                println("Cutaway $seed: 24 rotating 480x360 frames in $elapsed (no mesh uploads)")
            } finally {
                draw.destroy()
                params.destroy()
                destroyContext(gl)
            }
        }
    }

    @Test
    fun fragmentCutExposesBackFacesAndClipsTheFrontInViewSpace() = runTest {
        fun configuration(cut: String = "") = "s(C)v(env(n)d(f)fr(0)$cut)"
        val width = 320
        val height = 240
        val uncut = renderConfiguration(configuration(), width, height)
        val frontLimit = renderConfiguration(configuration("c(y)cp(1)"), width, height)
        val centerCut = renderConfiguration(configuration("c(y)cp(0)"), width, height)
        val defaultCut = renderConfiguration(configuration("c(y)"), width, height)
        val halfRadiusCut = renderConfiguration(configuration("c(y)cp(0.5)"), width, height)
        val backLimit = renderConfiguration(configuration("c(y)cp(-1)"), width, height)

        assertEquals(0, changedPixels(defaultCut, halfRadiusCut), "The default cut is at +0.5 base radius")
        assertTrue(changedPixels(defaultCut, centerCut) > 500, "The default retains more of the front shell")

        assertTrue(
            changedPixels(uncut, frontLimit) < 10,
            "A cut at +1 base radius must leave the normalized cube unchanged",
        )
        assertTrue(
            changedPixels(uncut, centerCut) > 1_000,
            "A center cut must remove a substantial part of the front surface",
        )
        assertTrue(
            !centerCut.isBackground(width / 2, height / 2),
            "The newly exposed back face must remain visible with face culling disabled",
        )
        assertTrue(
            nonBackgroundPixels(backLimit) < 10,
            "A cut at -1 base radius must remove the normalized cube",
        )
    }

    @Test
    fun rendersExactImmersedAntiprismConfigurationWithActualShaders() = runTest {
        val prefix = "a(r(n))s(SA5_2)"
        val suffix =
            "v(r(-42,-22.1,-110.3)s(0.11)fw(0.06666667)fr(0.03333333))" +
            "p(l(0.55)c(0.16)h(298))e(s(60))"
        val image = renderConfiguration(
            prefix + "hf(γ,β,α)" + suffix,
            width = 480,
            height = 360,
        )
        val facesShown = renderConfiguration(prefix + suffix, width = 480, height = 360)

        assertEquals(480 * 360 * 4, image.rgba.length)
        var nonBackground = 0
        var opaque = 0
        var changedByHiddenFaces = 0
        for (offset in 0 until image.rgba.length step 4) {
            val red = image.rgba.unsigned(offset)
            val green = image.rgba.unsigned(offset + 1)
            val blue = image.rgba.unsigned(offset + 2)
            val alpha = image.rgba.unsigned(offset + 3)
            if (alpha == 255) opaque++
            if (red !in 240..244 || green !in 240..244 || blue !in 240..244) nonBackground++
            if (
                red != facesShown.rgba.unsigned(offset) ||
                green != facesShown.rgba.unsigned(offset + 1) ||
                blue != facesShown.rgba.unsigned(offset + 2)
            ) {
                changedByHiddenFaces++
            }
        }
        assertEquals(480 * 360, opaque, "The PNG input must be fully composited over the page background")
        assertTrue(nonBackground > 10_000, "The image must contain the rendered polyhedron and table")
        assertTrue(changedByHiddenFaces > 5_000, "Serialized hidden face orbits must switch to rim geometry")
    }
}

private fun changedPixels(first: RenderedImage, second: RenderedImage): Int {
    assertEquals(first.rgba.length, second.rgba.length)
    var changed = 0
    for (offset in 0 until first.rgba.length step 4) {
        if (
            first.rgba.unsigned(offset) != second.rgba.unsigned(offset) ||
            first.rgba.unsigned(offset + 1) != second.rgba.unsigned(offset + 1) ||
            first.rgba.unsigned(offset + 2) != second.rgba.unsigned(offset + 2)
        ) {
            changed++
        }
    }
    return changed
}

private fun nonBackgroundPixels(image: RenderedImage): Int {
    var count = 0
    for (offset in 0 until image.rgba.length step 4) {
        val red = image.rgba.unsigned(offset)
        val green = image.rgba.unsigned(offset + 1)
        val blue = image.rgba.unsigned(offset + 2)
        if (red !in 240..244 || green !in 240..244 || blue !in 240..244) count++
    }
    return count
}

private fun RenderedImage.isBackground(x: Int, y: Int): Boolean {
    val offset = (y * width + x) * 4
    return rgba.unsigned(offset) in 240..244 &&
        rgba.unsigned(offset + 1) in 240..244 &&
        rgba.unsigned(offset + 2) in 240..244
}

private fun org.khronos.webgl.Uint8Array.unsigned(index: Int): Int =
    js("(pixels, index) => pixels[index]").unsafeCast<(org.khronos.webgl.Uint8Array, Int) -> Int>()(this, index)
