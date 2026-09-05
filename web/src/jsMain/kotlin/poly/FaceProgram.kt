/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import polyhedra.web.glsl.*
import kotlin.math.PI
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceProgram(gl: GL) : ViewBaseProgram(gl) {
    val uLightColor by uniform(GLType.vec3)
    val uFillColor by uniform(GLType.vec3)
    val uLightPosition by uniform(GLType.vec3)
    val uKeyLightIntensity by uniform(GLType.float)
    val uFillLightIntensity by uniform(GLType.float)
    val uRoughness by uniform(GLType.float)
    val uFresnelF0 by uniform(GLType.float)
    val uInteriorRadius by uniform(GLType.float)

    val uTargetFraction by uniform(GLType.float)
    val uPrevFraction by uniform(GLType.float)

    val aPosition by attribute(GLType.vec3)
    val aLightNormal by attribute(GLType.vec3)
    val aExpandDir by attribute(GLType.vec3)
    val aThicknessDir by attribute(GLType.vec3)
    val aRimDir by attribute(GLType.vec3)
    val aRimMax by attribute(GLType.float)
    val aColor by attribute(GLType.vec3, GLPrecision.lowp)

    val aPrevPosition by attribute(GLType.vec3)
    val aPrevLightNormal by attribute(GLType.vec3)
    val aPrevExpandDir by attribute(GLType.vec3)
    val aPrevThicknessDir by attribute(GLType.vec3)
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
    private val vSurfaceSide by varying(GLType.float, GLPrecision.lowp)

    /** Schlick's inexpensive approximation of dielectric Fresnel reflectance. */
    private val fSchlickFresnel by function(
        GLType.float,
        "cosTheta", GLType.float,
        "f0", GLType.float,
    ) { cosTheta, f0 ->
        val oneMinusCosine by 1.0.literal - cosTheta
        f0 + (1.0.literal - f0) * pow(oneMinusCosine, 5.0)
    }

    /** Isotropic Trowbridge-Reitz/GGX microfacet normal distribution. */
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

    val fInterpolatedPosition by function(GLType.vec3) {
        val pos by aPosition * uTargetFraction + aPrevPosition * uPrevFraction
        val rd by aRimDir * min(uFaceRim, aRimMax) * uTargetFraction + aPrevRimDir * min(uFaceRim, aPrevRimMax) * uPrevFraction
        val thicknessDir by aThicknessDir * uTargetFraction + aPrevThicknessDir * uPrevFraction
        pos + rd - thicknessDir * aInner * uFaceWidth
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
        vCutDepth by position.z
        // lighting & color
        vNormal by fLightNormal()
        // Source-face orientation, not aInner (which also varies across rim walls).
        // This identifies explicit undersides as well as reversed outer-face fragments.
        vSurfaceSide by dot(normalize(fInterpolatedLightNormal()), normalize(fInterpolatedExpandDir()))
        vToCamera by uCameraPosition - position.xyz
        vToLight by uLightPosition - position.xyz
        vColor by fInterpolatedColor() * aFaceMode
        vColorAlpha by uColorAlpha * fCullMull(position, uNormalMatrix * fInterpolatedExpandDir())
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        discardCutFragments()
        // Keep the dielectric shading frame facing the viewer on two-sided surfaces.
        val normal by select(gl_FrontFacing, normalize(vNormal), normalize(vNormal) * -1.0)
        // A reversed material boundary always exposes its interior, even for an underside or
        // perpendicular rim wall. Flipping its BRDF normal must not turn it into an exterior.
        val interior by select(gl_FrontFacing, min(max(0.0.literal - vSurfaceSide, 0.0), 1.0), 1.0.literal)
        // Analytic cavity-light proxy: a disk aperture's view factor falls as R²/(R²+d²).
        // No scene visibility is traced. The nonzero floors keep inner structure legible, while
        // reduced incident light suppresses implausible exterior-like highlights on backsides.
        val cavityDepth by max(uCutPosition - vCutDepth, 0.0) * uCutEnabled / max(uInteriorRadius, 0.0001)
        val aperture by 1.0.literal / (1.0.literal + cavityDepth * cavityDepth)
        val keyAccess by 1.0.literal - interior * (0.94.literal - 0.28.literal * aperture)
        val fillAccess by 1.0.literal - interior * (0.75.literal - 0.40.literal * aperture)
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

        // Face colors are authored as sRGB; evaluate the BRDF in linear light.
        val baseColor by pow(max(vColor, 0.0), vec3(2.2))
        val diffuseBrdf by baseColor * ((1.0.literal - fresnel) / PI.literal)
        val specularBrdf by vec3(distribution * visibility * fresnel)
        // Normalize inverse-square falloff at the model origin, so the control remains intuitive.
        val keyAttenuation by dot(uLightPosition, uLightPosition) / max(dot(vToLight, vToLight), 0.01)
        val directLight by (diffuseBrdf + specularBrdf) * uLightColor * (
            noL * uKeyLightIntensity * keyAttenuation * keyAccess
        )

        // One constant environment term gives printed plastic visible fill and grazing reflection
        // without an environment texture or another rendering pass.
        val viewFresnel by fSchlickFresnel(noV, uFresnelF0)
        val fillDiffuse by (baseColor * uFillColor) * ((1.0.literal - viewFresnel) * uFillLightIntensity)
        val fillSpecular by uFillColor * (
            viewFresnel * uFillLightIntensity * (1.0.literal - uRoughness * 0.5.literal)
        )
        val linearColor by directLight + (fillDiffuse + fillSpecular) * fillAccess
        val displayColor by pow(max(linearColor, 0.0), vec3(1.0 / 2.2))
        gl_FragColor by vec4(displayColor, vColorAlpha)
    }
}

const val FACE_NORMAL = 1
const val FACE_SELECTED = 2
