/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.js.glsl.*
import org.khronos.webgl.WebGLRenderingContext as GL

class EdgeProgram(gl: GL) : ViewBaseProgram(gl) {
    val uVertexColor by uniform(GLType.vec4, GLPrecision.lowp)
    val uCullMode by uniform(GLType.float) // 0 - no, 1 - cull front, -1 - cull back

    val uTargetFraction by uniform(GLType.float)
    val uPrevFraction by uniform(GLType.float)

    val aPosition by attribute(GLType.vec3)
    val aNormal by attribute(GLType.vec3)

    val aPrevPosition by attribute(GLType.vec3)
    val aPrevNormal by attribute(GLType.vec3)

    private val vColorMul by varying(GLType.float)

    val fInterpolatedPosition by function(GLType.vec3) {
        aPosition * uTargetFraction + aPrevPosition * uPrevFraction
    }

    val fInterpolatedNormal by function(GLType.vec3) {
        aNormal * uTargetFraction + aPrevNormal * uPrevFraction
    }

    // world position of the current element
    val fPosition by function(GLType.vec4) {
        computePosition(fInterpolatedPosition(), fInterpolatedNormal())
    }

    // world normal of the current element
    val fNormal by function(GLType.vec3) {
        uNormalMatrix * fInterpolatedNormal()
    }

    // face direction: > 0 - front-face, < 0 - back-face
    val fFaceDirection by function(
        GLType.float,
        "position", GLType.vec4,
        "normal", GLType.vec3
    ) { position, normal ->
        dot((position.xyz - uCameraPosition), normal)
    }

    override val vertexShader = shader(ShaderType.Vertex) {
        val position by fPosition()
        gl_Position by uProjectionMatrix * position
        vColorMul by select(
            uCullMode eq 0.0.literal,
            1.0.literal,
            select(fFaceDirection(position, fNormal()) * uCullMode ge 0.0.literal, 1.0.literal, 0.0.literal)
        )
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        gl_FragColor by uVertexColor * vColorMul
    }
}