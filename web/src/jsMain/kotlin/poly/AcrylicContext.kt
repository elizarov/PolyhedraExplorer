package polyhedra.web.poly

import org.khronos.webgl.WebGLRenderingContext as GL
import polyhedra.web.glsl.*

/** Bounded two-layer screen-space transmission. One cached texture; no CPU sorting or readback. */
class AcrylicContext(private val gl: GL) {
    private val scene = requireNotNull(gl.createTexture())
    private var textureWidth = 0
    private var textureHeight = 0

    init {
        gl.bindTexture(GL.TEXTURE_2D, scene)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
    }

    fun draw(view: ViewContext, lighting: LightingContext, faces: FaceContext, edges: EdgeContext, width: Int, height: Int) {
        gl.activeTexture(GL.TEXTURE0)
        gl.bindTexture(GL.TEXTURE_2D, scene)
        faces.acrylicProgram.use {
            uSceneColor.textureUnit(0)
            uSceneSize by float32Of(width.toDouble(), height.toDouble())
        }
        try {
            // Each layer reads a snapshot, never its own partially rendered framebuffer. Depth
            // selects its closest fragment, making triangle order immaterial within the layer.
            for (cullMode in listOf(1, -1)) {
                if (textureWidth != width || textureHeight != height) {
                    gl.copyTexImage2D(GL.TEXTURE_2D, 0, GL.RGBA, 0, 0, width, height, 0)
                    textureWidth = width
                    textureHeight = height
                } else {
                    gl.copyTexSubImage2D(GL.TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
                }
                gl.depthMask(true)
                gl.clear(GL.DEPTH_BUFFER_BIT)
                gl[GL.DEPTH_TEST] = true
                gl[GL.BLEND] = false
                faces.draw(view, lighting, cullMode)
            }
            // Lines are an inspection overlay, not optical material. Draw every occurrence in
            // one stable pass: switching a source polygon from front to back must not move its
            // outlines into/out of the refracted background. Include edges seen through acrylic.
            gl[GL.BLEND] = true
            gl[GL.DEPTH_TEST] = false
            gl.depthMask(false)
            edges.draw(view)
        } finally {
            gl.depthMask(true)
            gl[GL.DEPTH_TEST] = true
            gl[GL.BLEND] = false
            gl.bindTexture(GL.TEXTURE_2D, null)
        }
    }

    fun destroy() { gl.deleteTexture(scene) }
}
