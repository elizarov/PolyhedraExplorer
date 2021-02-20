package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

abstract class SharedPolyProgram(gl: GL) : GLProgram(gl) {
    val uProjectionMatrix by uniform(GLType.mat4)
    val uModelViewMatrix by uniform(GLType.mat4)
    val uNormalMatrix by uniform(GLType.mat3)
    val uExpand by uniform(GLType.float)

    val aVertexPosition by attribute(GLType.vec3)
    val aVertexNormal by attribute(GLType.vec3)

    fun assignView(viewMatrices: ViewMatrices) {
        with(viewMatrices) {
            uProjectionMatrix.assign(projectionMatrix)
            uModelViewMatrix.assign(modelViewMatrix)
            uNormalMatrix.assign(normalMatrix)
            uExpand.assign(expand)
        }
    }
}

fun SharedPolyProgram.assignSharedPolyBuffers(sharedPolyBuffers: SharedPolyBuffers) {
    gl.bindBuffer(GL.ARRAY_BUFFER, sharedPolyBuffers.positionBuffer.glBuffer)
    aVertexPosition.enable()

    gl.bindBuffer(GL.ARRAY_BUFFER, sharedPolyBuffers.normalBuffer.glBuffer)
    aVertexNormal.enable()
}