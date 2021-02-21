package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.js.glsl.*
import org.khronos.webgl.WebGLRenderingContext as GL

abstract class SharedPolyProgram(gl: GL) : GLProgram(gl) {
    val uCameraPosition by uniform(GLType.vec3)
    val uProjectionMatrix by uniform(GLType.mat4)
    val uModelMatrix by uniform(GLType.mat4)
    val uNormalMatrix by uniform(GLType.mat3)
    val uExpand by uniform(GLType.float)
    val uColorAlpha by uniform(GLType.float, GLPrecision.lowp)

    val aVertexPosition by attribute(GLType.vec3)
    val aVertexNormal by attribute(GLType.vec3)

    fun assignView(view: ViewContext) {
        with(view) {
            uCameraPosition.assign(cameraPosition)
            uProjectionMatrix.assign(projectionMatrix)
            uModelMatrix.assign(modelMatrix)
            uNormalMatrix.assign(normalMatrix)
            with(params) {
                uExpand.assign(expand.value)
                uColorAlpha.assign(1.0 - transparent.value)
            }
        }
    }
}

fun SharedPolyProgram.assignSharedPolyBuffers(sharedPolyBuffers: SharedPolyBuffers) {
    gl.bindBuffer(GL.ARRAY_BUFFER, sharedPolyBuffers.positionBuffer.glBuffer)
    aVertexPosition.enable()

    gl.bindBuffer(GL.ARRAY_BUFFER, sharedPolyBuffers.normalBuffer.glBuffer)
    aVertexNormal.enable()
}