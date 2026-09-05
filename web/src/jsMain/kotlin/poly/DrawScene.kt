/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import org.w3c.dom.*
import polyhedra.web.glsl.*
import polyhedra.web.params.*
import org.khronos.webgl.WebGLRenderingContext as GL

class DrawContext(
    val gl: GL,
    params: RenderParams,
    private val onUpdate: () -> Unit,
) : Param.Context(params) {
    constructor(
        canvas: HTMLCanvasElement,
        params: RenderParams,
        onUpdate: () -> Unit,
    ) : this(
        canvas.getContext("webgl", js("({ premultipliedAlpha: false, stencil: true })") as Any) as GL,
        params,
        onUpdate,
    )

    val view = ViewContext(params.view)
    val lighting = LightingContext(params.lighting)
    val faces = FaceContext(gl, params)
    val edges = EdgeContext(gl, params)
    val vertices = VertexContext(gl, params)
    val environment = EnvironmentContext(gl, params)
    val symmetryOverlay = SymmetryOverlayContext(gl, params)
    private var acrylicContext: AcrylicContext? = null

    fun drawAcrylic(width: Int, height: Int) {
        val context = acrylicContext ?: AcrylicContext(gl).also { acrylicContext = it }
        context.draw(view, lighting, faces, edges, width, height)
    }

    override fun destroy() {
        acrylicContext?.destroy()
        super.destroy()
    }

    init {
        setup()
        initGL()
    }

    override fun updateAlways() {
        onUpdate()
    }
}

private fun DrawContext.initGL() {
    gl.blendFunc(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA)
    gl.depthFunc(GL.LEQUAL)
    gl.clearColor(0.0f, 0.0f, 0.0f, 0.0f)
    gl.clearDepth(1.0f)
    gl.clearStencil(0)
    gl.getExtension("OES_element_index_uint")
    gl[GL.CULL_FACE] = true
    gl.cullFace(GL.BACK)
}

fun DrawContext.drawScene() {
    drawScene(gl.canvas.width, gl.canvas.height)
}

fun DrawContext.drawScene(width: Int, height: Int) {
    view.initProjection(width, height)
    gl.viewport(0, 0, width, height)
    gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT or GL.STENCIL_BUFFER_BIT)

    environment.draw(view, lighting, faces)

    val transparentFaces = faces.drawFaces && view.transparencyEnabled
    gl[GL.DEPTH_TEST] = true
    gl[GL.BLEND] = false
    if (transparentFaces) {
        drawAcrylic(width, height)
    } else {
        drawOpaqueFacesAndEdges(gl, view, lighting, faces, edges)
    }
    gl[GL.DEPTH_TEST] = true
    gl[GL.BLEND] = false
    symmetryOverlay.draw(view)
    vertices.draw(view)
}

internal fun drawOpaqueFacesAndEdges(
    gl: GL,
    view: ViewContext,
    lighting: LightingContext,
    faces: FaceContext,
    edges: EdgeContext,
) {
    faces.draw(view, lighting)
    // Edge occurrences are stored once per adjacent face. Keep only occurrences belonging to a
    // front-facing face, just as triangle rasterization does for the face itself. The edge shader
    // represents culled fragments with zero alpha, so compositing must remain enabled; without it
    // those fragments overwrite opaque face pixels with transparent holes. Lines are an overlay
    // and must not alter the depth buffer used by the later symmetry and vertex passes.
    gl[GL.BLEND] = true
    gl.depthMask(false)
    try {
        edges.draw(view, -1)
    } finally {
        gl.depthMask(true)
        gl[GL.BLEND] = false
    }
}

