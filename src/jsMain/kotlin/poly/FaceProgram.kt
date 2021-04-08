/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.js.glsl.*
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceProgram(gl: GL) : PolyProgram(gl) {
    val uAmbientLightColor by uniform(GLType.vec3)
    val uDiffuseLightColor by uniform(GLType.vec3)
    val uSpecularLightColor by uniform(GLType.vec3)
    val uSpecularLightPower by uniform(GLType.float)
    val uLightPosition by uniform(GLType.vec3)

    val aVertexColor by attribute(GLType.vec3, GLPrecision.lowp)
    val aPrevVertexColor by attribute(GLType.vec3, GLPrecision.lowp)

    val aFaceMode by attribute(GLType.float, GLPrecision.lowp)

    private val vNormal by varying(GLType.vec3)
    private val vToCamera by varying(GLType.vec3)
    private val vToLight by varying(GLType.vec3)
    private val vColor by varying(GLType.vec3, GLPrecision.lowp)

    val fInterpolatedColor by function(GLType.vec3) {
        aVertexColor * uTargetFraction + aPrevVertexColor * uPrevFraction
    }

    override val vertexShader = shader(ShaderType.Vertex) {
        // position
        val position by fPosition()
        gl_Position by uProjectionMatrix * position
        // lighting & color
        vNormal by fNormal()
        vToCamera by uCameraPosition - position.xyz
        vToLight by uLightPosition - position.xyz
        vColor by fInterpolatedColor() * aFaceMode
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        val normToCamera by normalize(vToCamera)
        val normToLight by normalize(vToLight)
        val halfVector by normalize(normToCamera + normToLight)
        val light by uAmbientLightColor + uDiffuseLightColor * max(dot(vNormal, normToLight), 0.0)
        val specular by uSpecularLightColor * pow(max(dot(vNormal, halfVector), 0.0), uSpecularLightPower)
        gl_FragColor by vec4(vColor * light + specular, uColorAlpha)
    }
}

const val FACE_NORMAL = 1
const val FACE_SELECTED = 2