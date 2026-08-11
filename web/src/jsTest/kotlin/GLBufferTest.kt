package polyhedra.web

import kotlinx.browser.document
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement
import polyhedra.web.glsl.GLType
import polyhedra.web.glsl.createBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame

class GLBufferTest {
    @Test
    fun growingMeshBufferDoesNotRetainAnUnusedUploadTail() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val buffer = createBuffer(gl, GLType.vec3)

        buffer.ensureCapacity(4_860)
        assertEquals(4_860 * GLType.vec3.bufferSize, buffer.data.length)
        buffer.bindBufferData(gl)
        val epsilonGlBuffer = buffer.glBuffer

        // Regression sizes for Kis epsilon -> Kis zeta in s(sD)t(k,s,o,k[...]).
        buffer.ensureCapacity(5_700)
        assertEquals(5_700 * GLType.vec3.bufferSize, buffer.data.length)
        buffer.bindBufferData(gl)
        assertNotSame(epsilonGlBuffer, buffer.glBuffer)
    }

    @Test
    fun replacingMeshBufferDetachesItsEnabledVertexAttributes() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val buffer = createBuffer(gl, GLType.vec3)

        buffer.bindBufferData(gl)
        gl.vertexAttribPointer(0, 3, WebGLRenderingContext.FLOAT, false, 0, 0)
        gl.enableVertexAttribArray(0)

        buffer.ensureCapacity(1_000)
        buffer.bindBufferData(gl)

        assertFalse(
            gl.getVertexAttrib(0, WebGLRenderingContext.VERTEX_ATTRIB_ARRAY_ENABLED) as Boolean,
            "A draw between buffer replacement and mesh rebinding must not use a deleted buffer",
        )
    }
}
