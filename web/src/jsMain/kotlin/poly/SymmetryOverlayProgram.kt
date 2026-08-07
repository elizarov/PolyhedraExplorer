/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import polyhedra.web.glsl.*
import org.khronos.webgl.WebGLRenderingContext as GL

class SymmetryOverlayProgram(gl: GL) : ViewBaseProgram(gl) {
    val uColor by uniform(GLType.vec4, GLPrecision.lowp)
    val aPosition by attribute(GLType.vec3)

    override val vertexShader = shader(ShaderType.Vertex) {
        gl_Position by uProjectionMatrix * uModelMatrix * vec4(aPosition, 1.0)
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        gl_FragColor by uColor
    }
}
