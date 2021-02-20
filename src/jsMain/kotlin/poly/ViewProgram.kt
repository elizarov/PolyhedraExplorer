package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

abstract class ViewProgram(gl: GL) : GLProgram(gl) {
    val uProjectionMatrix by uniform(GLType.mat4)
    val uModelViewMatrix by uniform(GLType.mat4)
    val uNormalMatrix by uniform(GLType.mat3)
    val uExpand by uniform(GLType.float)

    fun assignView(viewMatrices: ViewMatrices) {
        with(viewMatrices) {
            uProjectionMatrix.assign(projectionMatrix)
            uModelViewMatrix.assign(modelViewMatrix)
            uNormalMatrix.assign(normalMatrix)
            uExpand.assign(expand)
        }
    }
}