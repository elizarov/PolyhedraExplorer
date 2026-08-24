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
import polyhedra.web.poly.EdgeContext
import polyhedra.web.poly.FaceContext
import polyhedra.web.poly.LightingContext
import polyhedra.web.poly.RenderParams
import polyhedra.web.poly.ViewContext
import polyhedra.web.poly.draw
import polyhedra.web.poly.drawOpaqueFacesAndEdges
import kotlin.test.Test
import kotlin.test.assertEquals

class EdgePresentationRenderingTest {
    @Test
    fun opaqueStarAntiprismEdgesNeverFloatAwayFromRenderedMaterial() {
        val poly = requireNotNull("SA5_2".toSeedOrNull()).poly
        val params = RenderParams("", null)
        params.loadFromString(
            "hf(γ,β,α)v(r(-42,-22.1,-110.3)s(0.22)fw(0.06666667)fr(0.03333333))",
        )
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
        val edges = EdgeContext(gl, params) { poly }
        try {
            initialize(gl)
            faces.performUpdate(null, 0.0)
            edges.performUpdate(null, 0.0)
            val facePixels = render(gl, canvas, view) {
                faces.draw(view, lighting)
            }
            val combinedPixels = render(gl, canvas, view) {
                drawOpaqueFacesAndEdges(gl, view, lighting, faces, edges)
            }

            val unsupported = unsupportedEdgePixels(
                facePixels,
                combinedPixels,
                canvas.width,
                canvas.height,
            )
            val erased = erasedFacePixels(facePixels, combinedPixels)
            assertEquals(
                0,
                unsupported,
                "Opaque edge rendering produced $unsupported pixels without supporting face material",
            )
            assertEquals(
                0,
                erased,
                "Opaque edge culling erased $erased pixels from rendered face material",
            )
        } finally {
            edges.destroy()
            faces.destroy()
            lighting.destroy()
            view.destroy()
            params.destroy()
        }
    }

    private fun initialize(gl: WebGLRenderingContext) {
        gl.blendFunc(WebGLRenderingContext.SRC_ALPHA, WebGLRenderingContext.ONE_MINUS_SRC_ALPHA)
        gl.depthFunc(WebGLRenderingContext.LEQUAL)
        gl.clearColor(0.0f, 0.0f, 0.0f, 0.0f)
        gl.clearDepth(1.0f)
        gl.clearStencil(0)
        gl.getExtension("OES_element_index_uint")
        gl[WebGLRenderingContext.CULL_FACE] = true
        gl.cullFace(WebGLRenderingContext.BACK)
        gl[WebGLRenderingContext.DEPTH_TEST] = true
        gl[WebGLRenderingContext.BLEND] = false
    }

    private fun render(
        gl: WebGLRenderingContext,
        canvas: HTMLCanvasElement,
        view: ViewContext,
        draw: () -> Unit,
    ): Uint8Array {
        view.initProjection(canvas.width, canvas.height)
        gl.viewport(0, 0, canvas.width, canvas.height)
        gl.clear(
            WebGLRenderingContext.COLOR_BUFFER_BIT or
                WebGLRenderingContext.DEPTH_BUFFER_BIT or
                WebGLRenderingContext.STENCIL_BUFFER_BIT,
        )
        draw()
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

    private fun unsupportedEdgePixels(
        faces: Uint8Array,
        combined: Uint8Array,
        width: Int,
        height: Int,
    ): Int {
        fun alpha(pixels: Uint8Array, x: Int, y: Int): Int =
            pixels.unsigned((y * width + x) * 4 + 3)
        var unsupported = 0
        for (y in 0 until height) for (x in 0 until width) {
            if (alpha(combined, x, y) == 0 || alpha(faces, x, y) != 0) continue
            val supportedNearby = (maxOf(0, y - 1)..minOf(height - 1, y + 1)).any { nearbyY ->
                (maxOf(0, x - 1)..minOf(width - 1, x + 1)).any { nearbyX ->
                    alpha(faces, nearbyX, nearbyY) != 0
                }
            }
            if (!supportedNearby) unsupported++
        }
        return unsupported
    }

    private fun erasedFacePixels(faces: Uint8Array, combined: Uint8Array): Int {
        var erased = 0
        for (index in 3 until faces.length step 4) {
            if (faces.unsigned(index) != 0 && combined.unsigned(index) == 0) erased++
        }
        return erased
    }
}

private fun Uint8Array.unsigned(index: Int): Int =
    js("(pixels, index) => pixels[index]").unsafeCast<(Uint8Array, Int) -> Int>()(this, index)
