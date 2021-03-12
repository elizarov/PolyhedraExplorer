/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import kotlinext.js.*
import org.w3c.dom.*
import polyhedra.js.glsl.*
import polyhedra.js.main.*
import polyhedra.js.params.*
import org.khronos.webgl.WebGLRenderingContext as GL

class DrawContext(
    canvas: HTMLCanvasElement,
    override val params: RenderParams,
    private val onUpdate: () -> Unit,
) : Param.Context() {
    val gl: GL = canvas.getContext("webgl", js {
        premultipliedAlpha = false  // Ask for non-premultiplied alpha
    } as Any) as GL

    val view = ViewContext(params.view)
    val lightning = LightningContext(params.lighting)
    
    val polyContext = PolyContext(gl, params.poly)
    val faceContext = FaceContext(gl, polyContext, params.poly)
    val edgeContext = EdgeContext(gl, polyContext, params.poly)

    init {
        setup()
        initGL()
    }

    override fun update() {
        onUpdate()
    }
}

private fun DrawContext.initGL() {
    gl.blendFunc(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA);
    gl.depthFunc(GL.LEQUAL)
    gl.clearColor(0.0f, 0.0f, 0.0f, 0.0f)
    gl.clearDepth(1.0f)
}

fun DrawContext.drawScene() {
    val width = gl.canvas.width
    val height = gl.canvas.height

    view.initProjection(width, height)
    gl.viewport(0, 0, width, height)
    gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)

    val transparentFaces = params.view.transparentFaces.value
    val display = params.view.display.value
    val hasFaces = display.hasFaces() && transparentFaces < 1.0
    val hasEdges = display.hasEdges()
    val transparent = transparentFaces != 0.0 && hasFaces
    gl[GL.DEPTH_TEST] = !transparent
    gl[GL.BLEND] = transparent
    if (transparent) {
        // special code for transparent faces - draw back faces, then front faces
        gl[GL.CULL_FACE] = true
        gl.cullFace(GL.FRONT)
        faceContext.draw(view, lightning)
        if (hasEdges) edgeContext.draw(view, 1)
        gl.cullFace(GL.BACK)
        faceContext.draw(view, lightning)
        if (hasEdges) edgeContext.draw(view, -1)
    } else {
        if (hasFaces) {
            // regular draw faces
            val solid = params.view.expandFaces.value == 0.0
            gl[GL.CULL_FACE] = solid // can cull faces when drawing solid
            faceContext.draw(view, lightning)
        }
        if (hasEdges) {
            edgeContext.draw(view)
        }
    }
}




