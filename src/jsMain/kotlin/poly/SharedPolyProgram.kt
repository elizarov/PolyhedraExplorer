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

    // world position of the current element
    val fPosition by function(GLType.vec4) {
        uModelMatrix * vec4(aVertexPosition + aVertexNormal * uExpand, 1.0)
    }

    // world normal of the current element
    val fNormal by function(GLType.vec3) {
        uNormalMatrix * aVertexNormal
    }

    // face direction: > 0 - front-face, < 0 - back-face
    val fFaceDirection by function(
        GLType.float,
        "position", GLType.vec4,
        "normal", GLType.vec3
    ) { position, normal ->
        dot((position.xyz - uCameraPosition), normal)
    }

    fun assignView(view: ViewContext) {
        with(view) {
            uCameraPosition by cameraPosition
            uProjectionMatrix by projectionMatrix
            uModelMatrix by modelMatrix
            uNormalMatrix by normalMatrix
            with(params) {
                uExpand by expandFaces.animatedValue
                uColorAlpha by 1.0 - transparentFaces.animatedValue
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