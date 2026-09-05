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
        fun snapshot() {
            if (textureWidth != width || textureHeight != height) {
                gl.copyTexImage2D(GL.TEXTURE_2D, 0, GL.RGBA, 0, 0, width, height, 0)
                textureWidth = width
                textureHeight = height
            } else {
                gl.copyTexSubImage2D(GL.TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
            }
        }
        try {
            snapshot()
            gl.depthMask(true)
            gl.clear(GL.DEPTH_BUFFER_BIT)
            gl[GL.DEPTH_TEST] = true
            gl[GL.BLEND] = false
            faces.draw(view, lighting, 1)

            val hasEdges = edges.drawEdges || edges.selectedIndexSize > 0
            gl.clear(GL.DEPTH_BUFFER_BIT)
            if (hasEdges) {
                // Partition lines by visibility against the front material, not source-face
                // orientation. This also works for rim walls, cut faces and expanded geometry.
                // A minimal shader writes depth only, with the same animated positions/cut.
                gl.colorMask(false, false, false, false)
                gl[GL.POLYGON_OFFSET_FILL] = true
                gl.polygonOffset(1.0f, 1.0f)
                faces.draw(view, lighting, -1, depthOnly = true)
                gl[GL.POLYGON_OFFSET_FILL] = false
                gl.colorMask(true, true, true, true)
                gl.depthMask(false)
                gl.depthFunc(GL.GREATER)
                gl[GL.BLEND] = true
                edges.draw(view)
                gl.depthFunc(GL.LEQUAL)
                gl[GL.BLEND] = false
            }
            // Rear edges and rear faces are now in the same snapshot, so refraction, blur and
            // absorption affect them together. The front depth also prevents redrawing rear
            // lines afterward at their unrefracted positions.
            snapshot()
            faces.draw(view, lighting, -1)
            if (hasEdges) {
                gl[GL.BLEND] = true
                edges.draw(view)
            }
        } finally {
            gl.colorMask(true, true, true, true)
            gl[GL.POLYGON_OFFSET_FILL] = false
            gl.depthFunc(GL.LEQUAL)
            gl.depthMask(true)
            gl[GL.DEPTH_TEST] = true
            gl[GL.BLEND] = false
            gl.bindTexture(GL.TEXTURE_2D, null)
        }
    }

    fun destroy() { gl.deleteTexture(scene) }
}
