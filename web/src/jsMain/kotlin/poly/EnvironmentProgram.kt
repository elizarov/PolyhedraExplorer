/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import polyhedra.web.glsl.*
import kotlin.math.PI
import org.khronos.webgl.WebGLRenderingContext as GL

/** Physically based neutral-plastic receiver for the table environment. */
class TableProgram(gl: GL) : GLProgram(gl) {
    val uProjectionMatrix by uniform(GLType.mat4)
    val uCameraPosition by uniform(GLType.vec3)
    val uLightColor by uniform(GLType.vec3)
    val uFillColor by uniform(GLType.vec3)
    val uLightPosition by uniform(GLType.vec3)
    val uKeyLightIntensity by uniform(GLType.float)
    val uFillLightIntensity by uniform(GLType.float)
    val uRoughness by uniform(GLType.float)
    val uFresnelF0 by uniform(GLType.float)
    val uTableColor by uniform(GLType.vec3, GLPrecision.lowp)
    val uTableHeight by uniform(GLType.float)

    val aPosition by attribute(GLType.vec3)

    private val vToCamera by varying(GLType.vec3)
    private val vToLight by varying(GLType.vec3)

    private val fSchlickFresnel by function(
        GLType.float,
        "cosTheta", GLType.float,
        "f0", GLType.float,
    ) { cosTheta, f0 ->
        val oneMinusCosine by 1.0.literal - cosTheta
        f0 + (1.0.literal - f0) * pow(oneMinusCosine, 5.0)
    }

    private val fGgxDistribution by function(
        GLType.float,
        "roughness", GLType.float,
        "noH", GLType.float,
    ) { roughness, noH ->
        val alpha by roughness * roughness
        val alphaSquared by alpha * alpha
        val denominator by noH * noH * (alphaSquared - 1.0.literal) + 1.0.literal
        alphaSquared / (PI.literal * denominator * denominator)
    }

    override val vertexShader = shader(ShaderType.Vertex) {
        val position by aPosition + vec3(0.0.literal, uTableHeight, 0.0.literal)
        gl_Position by uProjectionMatrix * vec4(position, 1.0)
        vToCamera by uCameraPosition - position
        vToLight by uLightPosition - position
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        val normal by vec3(0.0, 1.0, 0.0)
        val toCamera by normalize(vToCamera)
        val toLight by normalize(vToLight)
        val halfVector by normalize(toCamera + toLight)
        val noV by max(dot(normal, toCamera), 0.0001)
        val noL by max(dot(normal, toLight), 0.0)
        val noH by max(dot(normal, halfVector), 0.0)
        val voH by max(dot(toCamera, halfVector), 0.0)

        val alpha by uRoughness * uRoughness
        val alphaSquared by alpha * alpha
        val ggxV by noL * sqrt(noV * noV * (1.0.literal - alphaSquared) + alphaSquared)
        val ggxL by noV * sqrt(noL * noL * (1.0.literal - alphaSquared) + alphaSquared)
        val visibility by 0.5.literal / max(ggxV + ggxL, 0.0001)
        val distribution by fGgxDistribution(uRoughness, noH)
        val fresnel by fSchlickFresnel(voH, uFresnelF0)

        val baseColor by pow(max(uTableColor, 0.0), vec3(2.2))
        val diffuseBrdf by baseColor * ((1.0.literal - fresnel) / PI.literal)
        val specularBrdf by vec3(distribution * visibility * fresnel)
        val keyAttenuation by dot(uLightPosition, uLightPosition) / max(dot(vToLight, vToLight), 0.01)
        val directLight by (diffuseBrdf + specularBrdf) * uLightColor * (
            noL * uKeyLightIntensity * keyAttenuation
        )

        val viewFresnel by fSchlickFresnel(noV, uFresnelF0)
        val fillDiffuse by (baseColor * uFillColor) * ((1.0.literal - viewFresnel) * uFillLightIntensity)
        val fillSpecular by uFillColor * (
            viewFresnel * uFillLightIntensity * (1.0.literal - uRoughness * 0.5.literal)
        )
        val linearColor by directLight + fillDiffuse + fillSpecular
        val displayColor by pow(max(linearColor, 0.0), vec3(1.0 / 2.2))
        gl_FragColor by vec4(displayColor, 1.0)
    }
}

/** Projects the animated face mesh from a point light onto the horizontal table plane. */
class TableShadowProgram(gl: GL) : ViewBaseProgram(gl) {
    val uLightPosition by uniform(GLType.vec3)
    val uTableHeight by uniform(GLType.float)
    val uShadowLift by uniform(GLType.float)
    val uShadowOpacity by uniform(GLType.float, GLPrecision.lowp)
    val uTargetFraction by uniform(GLType.float)
    val uPrevFraction by uniform(GLType.float)

    val aPosition by attribute(GLType.vec3)
    val aExpandDir by attribute(GLType.vec3)
    val aThicknessDir by attribute(GLType.vec3)
    val aRimDir by attribute(GLType.vec3)
    val aRimMax by attribute(GLType.float)
    val aPrevPosition by attribute(GLType.vec3)
    val aPrevExpandDir by attribute(GLType.vec3)
    val aPrevThicknessDir by attribute(GLType.vec3)
    val aPrevRimDir by attribute(GLType.vec3)
    val aPrevRimMax by attribute(GLType.float)
    val aInner by attribute(GLType.float, GLPrecision.lowp)

    private val fInterpolatedPosition by function(GLType.vec3) {
        val pos by aPosition * uTargetFraction + aPrevPosition * uPrevFraction
        val rimDirection by aRimDir * min(uFaceRim, aRimMax) * uTargetFraction +
            aPrevRimDir * min(uFaceRim, aPrevRimMax) * uPrevFraction
        val thicknessDirection by normalize(
            aThicknessDir * uTargetFraction + aPrevThicknessDir * uPrevFraction
        )
        pos + rimDirection - thicknessDirection * aInner * uFaceWidth
    }

    private val fInterpolatedExpandDir by function(GLType.vec3) {
        aExpandDir * uTargetFraction + aPrevExpandDir * uPrevFraction
    }

    override val vertexShader = shader(ShaderType.Vertex) {
        val position by fViewPosition(fInterpolatedPosition(), fInterpolatedExpandDir()).xyz
        val lightRay by position - uLightPosition
        val intersection by (uTableHeight - uLightPosition.y) / lightRay.y
        val projected by uLightPosition + lightRay * intersection +
            vec3(0.0.literal, uShadowLift, 0.0.literal)
        gl_Position by uProjectionMatrix * vec4(projected, 1.0)
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        gl_FragColor by vec4(0.0.literal, 0.0.literal, 0.0.literal, uShadowOpacity)
    }
}
