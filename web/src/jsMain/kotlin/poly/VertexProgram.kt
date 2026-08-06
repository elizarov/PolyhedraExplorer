/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import polyhedra.web.glsl.*
import org.khronos.webgl.WebGLRenderingContext as GL

class VertexProgram(gl: GL) : ViewBaseProgram(gl) {
    val uVertexColor by uniform(GLType.vec4, GLPrecision.lowp)
    val uTargetFraction by uniform(GLType.float)
    val uPrevFraction by uniform(GLType.float)

    val aPosition by attribute(GLType.vec3)
    val aNormal by attribute(GLType.vec3)
    val aPrevPosition by attribute(GLType.vec3)

    private val vShade by varying(GLType.float, GLPrecision.lowp)

    override val vertexShader = shader(ShaderType.Vertex) {
        val interpolatedPosition by aPosition * uTargetFraction + aPrevPosition * uPrevFraction
        val position by uModelMatrix * vec4(interpolatedPosition, 1.0)
        val normal by normalize(uNormalMatrix * aNormal)
        val toCamera by normalize(uCameraPosition - position.xyz)
        gl_Position by uProjectionMatrix * position
        vShade by 0.45.literal + 0.55.literal * max(dot(normal, toCamera), 0.0)
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        gl_FragColor by uVertexColor * vShade
    }
}
