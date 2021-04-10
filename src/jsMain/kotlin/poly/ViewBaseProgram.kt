/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.js.glsl.*
import org.khronos.webgl.WebGLRenderingContext as GL

abstract class ViewBaseProgram(gl: GL) : GLProgram(gl) {
    val uCameraPosition by uniform(GLType.vec3)
    val uProjectionMatrix by uniform(GLType.mat4)
    val uModelMatrix by uniform(GLType.mat4)
    val uNormalMatrix by uniform(GLType.mat3)
    val uExpand by uniform(GLType.float)
    val uColorAlpha by uniform(GLType.float, GLPrecision.lowp)
    val uFaceWidth by uniform(GLType.float)
    val uFaceRim by uniform(GLType.float)

    fun computePosition(position: GLExpr<GLType.vec3>, expandDir: GLExpr<GLType.vec3>) =
        // todo: optimize when not expanded?
        uModelMatrix * vec4(position + expandDir * uExpand, 1.0)

    fun assignView(view: ViewContext) {
        with(view) {
            uCameraPosition by cameraPosition
            uProjectionMatrix by projectionMatrix
            uModelMatrix by modelMatrix
            uNormalMatrix by normalMatrix
            uExpand by expandFaces
            uColorAlpha by 1.0 - transparentFaces
            uFaceWidth by faceWidth
            uFaceRim by faceRim
        }
    }
}