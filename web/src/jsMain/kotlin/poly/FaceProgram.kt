/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.js.glsl.*
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceProgram(gl: GL) : ViewBaseProgram(gl) {
    val uAmbientLightColor by uniform(GLType.vec3)
    val uDiffuseLightColor by uniform(GLType.vec3)
    val uSpecularLightColor by uniform(GLType.vec3)
    val uSpecularLightPower by uniform(GLType.float)
    val uLightPosition by uniform(GLType.vec3)

    val uTargetFraction by uniform(GLType.float)
    val uPrevFraction by uniform(GLType.float)

    val aPosition by attribute(GLType.vec3)
    val aLightNormal by attribute(GLType.vec3)
    val aExpandDir by attribute(GLType.vec3)
    val aRimDir by attribute(GLType.vec3)
    val aRimMax by attribute(GLType.float)
    val aColor by attribute(GLType.vec3, GLPrecision.lowp)

    val aPrevPosition by attribute(GLType.vec3)
    val aPrevLightNormal by attribute(GLType.vec3)
    val aPrevExpandDir by attribute(GLType.vec3)
    val aPrevRimDir by attribute(GLType.vec3)
    val aPrevRimMax by attribute(GLType.float)
    val aPrevColor by attribute(GLType.vec3, GLPrecision.lowp)

    val aInner by attribute(GLType.float, GLPrecision.lowp)
    val aFaceMode by attribute(GLType.float, GLPrecision.lowp)

    private val vNormal by varying(GLType.vec3)
    private val vToCamera by varying(GLType.vec3)
    private val vToLight by varying(GLType.vec3)
    private val vColor by varying(GLType.vec3, GLPrecision.lowp)
    private val vColorAlpha by varying(GLType.float, GLPrecision.lowp)

    val fInterpolatedPosition by function(GLType.vec3) {
        val pos by aPosition * uTargetFraction + aPrevPosition * uPrevFraction
        val rd by aRimDir * min(uFaceRim, aRimMax) * uTargetFraction + aPrevRimDir * min(uFaceRim, aPrevRimMax) * uPrevFraction
        val diLen by aInner * uFaceWidth
        val posLen by length(pos)
        val di by pos * diLen / posLen
        pos - di + rd * (posLen - diLen) / posLen
    }

    val fInterpolatedLightNormal by function(GLType.vec3) {
        aLightNormal * uTargetFraction + aPrevLightNormal * uPrevFraction
    }

    val fInterpolatedExpandDir by function(GLType.vec3) {
        aExpandDir * uTargetFraction + aPrevExpandDir * uPrevFraction
    }

    // world position of the current element
    val fPosition by function(GLType.vec4) {
        fViewPosition(fInterpolatedPosition(), fInterpolatedExpandDir())
    }

    // world normal of the current element
    val fLightNormal by function(GLType.vec3) {
        uNormalMatrix * fInterpolatedLightNormal()
    }

    val fInterpolatedColor by function(GLType.vec3) {
        aColor * uTargetFraction + aPrevColor * uPrevFraction
    }
    
    override val vertexShader = shader(ShaderType.Vertex) {
        // position
        val position by fPosition()
        gl_Position by uProjectionMatrix * position
        // lighting & color
        vNormal by fLightNormal()
        vToCamera by uCameraPosition - position.xyz
        vToLight by uLightPosition - position.xyz
        vColor by fInterpolatedColor() * aFaceMode
        vColorAlpha by uColorAlpha * fCullMull(position, uNormalMatrix * fInterpolatedExpandDir())
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        val normToCamera by normalize(vToCamera)
        val normToLight by normalize(vToLight)
        val halfVector by normalize(normToCamera + normToLight)
        val light by uAmbientLightColor + uDiffuseLightColor * max(dot(vNormal, normToLight), 0.0)
        val specular by uSpecularLightColor * pow(max(dot(vNormal, halfVector), 0.0), uSpecularLightPower)
        gl_FragColor by vec4(vColor * light + specular, vColorAlpha)
    }
}

const val FACE_NORMAL = 1
const val FACE_SELECTED = 2