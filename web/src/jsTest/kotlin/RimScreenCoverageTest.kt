/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web

import kotlinx.browser.document
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement
import polyhedra.core.poly.resolvedRims
import polyhedra.core.poly.toSeedOrNull
import polyhedra.web.glsl.set
import polyhedra.web.params.Param
import polyhedra.web.params.loadFromString
import polyhedra.web.poly.FaceContext
import polyhedra.web.poly.LightingContext
import polyhedra.web.poly.RenderParams
import polyhedra.web.poly.ViewContext
import polyhedra.web.poly.draw
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Raster-level checks that immersed presentations never expose cull-dependent background holes. */
class RimScreenCoverageTest {
    @Test
    fun starAntiprismFiveHalvesHasNoCullDependentBackgroundLeaks() {
        assertTwoSidedCoverage(
            "SA5_2",
            "r(-42,-22.1,-110.3)s(0.22)fw(0.06666667)fr(0.03333333)d(f)",
        )
    }

    @Test
    fun immersedRimsKeepTwoSidedCoverageAcrossViewsAndDimensions() {
        val cases = listOf(
            "SA5_2" to "r(25,70,-15)s(0.22)fw(0.1)fr(0.015)d(f)",
            "SP5_2" to "r(132.3,14.4,-85)s(0.22)fw(0.112)fr(0.12)d(f)",
            "SY5_2" to "r(-166.4,-4.5,178.1)s(0.22)fw(0.1)fr(0.05)d(f)",
            "SD" to "r(102.6,-34,-79.6)s(0.22)fw(0.129)fr(0.015)d(f)",
        )
        for ((tag, view) in cases) assertTwoSidedCoverage(tag, view)
    }

    private fun assertTwoSidedCoverage(tag: String, viewParameters: String) {
        val poly = requireNotNull(tag.toSeedOrNull()).poly
        assertTrue(
            poly.resolvedFaces.any { face -> face.sourceBoundarySelfIntersects },
            "$tag must exercise an immersed presentation",
        )
        val params = RenderParams("", null)
        params.loadFromString("v($viewParameters)")
        params.poly.hideFaces.updateValue(poly.faceKinds.keys, Param.TargetValue)
        params.poly.updateResolvedRims(
            poly.resolvedRims(params.view.faceRim.targetValue, params.view.faceWidth.targetValue),
        )
        params.performUpdate(null, 0.0)

        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 800
        canvas.height = 600
        val gl = requireNotNull(
            canvas.getContext(
                "webgl",
                js("({ alpha: true, antialias: false, premultipliedAlpha: false, stencil: true })"),
            ) as? WebGLRenderingContext,
        )
        val view = ViewContext(params.view)
        val lighting = LightingContext(params.lighting)
        val faces = FaceContext(gl, params) { poly }
        try {
            initialize(gl)
            faces.performUpdate(null, 0.0)
            val culled = render(gl, canvas, view, lighting, faces, cull = true)
            val twoSided = render(gl, canvas, view, lighting, faces, cull = false)
            assertTrue(
                materialPixelCount(twoSided) > 1_000,
                "$tag diagnostic view must contain enough rendered material",
            )
            assertEquals(
                0,
                missingMaterialPixels(culled, twoSided),
                "$tag exposes background where its two-sided rim presentation contains material",
            )
        } finally {
            faces.destroy()
            lighting.destroy()
            view.destroy()
            params.destroy()
        }
    }

    private fun initialize(gl: WebGLRenderingContext) {
        gl.depthFunc(WebGLRenderingContext.LEQUAL)
        gl.clearColor(0.0f, 0.0f, 0.0f, 0.0f)
        gl.clearDepth(1.0f)
        gl.clearStencil(0)
        gl.getExtension("OES_element_index_uint")
        gl.cullFace(WebGLRenderingContext.BACK)
        gl[WebGLRenderingContext.DEPTH_TEST] = true
        gl[WebGLRenderingContext.BLEND] = false
    }

    private fun render(
        gl: WebGLRenderingContext,
        canvas: HTMLCanvasElement,
        view: ViewContext,
        lighting: LightingContext,
        faces: FaceContext,
        cull: Boolean,
    ): Uint8Array {
        gl[WebGLRenderingContext.CULL_FACE] = cull
        view.initProjection(canvas.width, canvas.height)
        gl.viewport(0, 0, canvas.width, canvas.height)
        gl.clear(
            WebGLRenderingContext.COLOR_BUFFER_BIT or
                WebGLRenderingContext.DEPTH_BUFFER_BIT or
                WebGLRenderingContext.STENCIL_BUFFER_BIT,
        )
        faces.draw(view, lighting)
        assertEquals(cull, gl.isEnabled(WebGLRenderingContext.CULL_FACE), "Face rendering must restore culling state")
        return Uint8Array(canvas.width * canvas.height * 4).also { pixels ->
            gl.readPixels(
                0,
                0,
                canvas.width,
                canvas.height,
                WebGLRenderingContext.RGBA,
                WebGLRenderingContext.UNSIGNED_BYTE,
                pixels,
            )
        }
    }

    private fun missingMaterialPixels(culled: Uint8Array, twoSided: Uint8Array): Int {
        var count = 0
        for (alpha in 3 until culled.length step 4) {
            if (culled.unsigned(alpha) == 0 && twoSided.unsigned(alpha) != 0) count++
        }
        return count
    }

    private fun materialPixelCount(pixels: Uint8Array): Int {
        var count = 0
        for (alpha in 3 until pixels.length step 4) if (pixels.unsigned(alpha) != 0) count++
        return count
    }
}

private fun Uint8Array.unsigned(index: Int): Int =
    js("(pixels, index) => pixels[index]").unsafeCast<(Uint8Array, Int) -> Int>()(this, index)
