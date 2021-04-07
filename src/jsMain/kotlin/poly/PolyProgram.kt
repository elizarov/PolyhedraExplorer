/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.js.glsl.*
import org.khronos.webgl.WebGLRenderingContext as GL

abstract class PolyProgram(gl: GL) : GLProgram(gl) {
    val uCameraPosition by uniform(GLType.vec3)
    val uProjectionMatrix by uniform(GLType.mat4)
    val uModelMatrix by uniform(GLType.mat4)
    val uNormalMatrix by uniform(GLType.mat3)
    val uExpand by uniform(GLType.float)
    val uColorAlpha by uniform(GLType.float, GLPrecision.lowp)

    val aVertexPosition by attribute(GLType.vec3)
    val aVertexNormal by attribute(GLType.vec3)

    val aPrevVertexPosition by attribute(GLType.vec3)
    val aPrevVertexNormal by attribute(GLType.vec3)

    val uTargetFraction by uniform(GLType.float)
    val uPrevFraction by uniform(GLType.float)

    val fInterpolatedPosition by function(GLType.vec3) {
        aVertexPosition * uTargetFraction + aPrevVertexPosition * uPrevFraction
    }

    val fInterpolatedNormal by function(GLType.vec3) {
        aVertexNormal * uTargetFraction + aPrevVertexNormal * uPrevFraction
    }

    // world position of the current element
    val fPosition by function(GLType.vec4) {
        // todo: optimize when not expanded?
        uModelMatrix * vec4(fInterpolatedPosition() + fInterpolatedNormal() * uExpand, 1.0)
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

    fun assignView(view: ViewContext) {
        with(view) {
            uCameraPosition by cameraPosition
            uProjectionMatrix by projectionMatrix
            uModelMatrix by modelMatrix
            uNormalMatrix by normalMatrix
            uExpand by expandFaces
            uColorAlpha by 1.0 - transparentFaces
        }
    }
}

fun PolyProgram.assignPolyContext(polyContext: PolyContext) {
    aVertexPosition by polyContext.target.positionBuffer
    aVertexNormal by polyContext.target.normalBuffer
    val animation = polyContext.animation
    if (animation != null) {
        uTargetFraction by animation.targetFraction
        uPrevFraction by animation.prevFraction
        aPrevVertexPosition by polyContext.prev.positionBuffer
        aPrevVertexNormal by polyContext.prev.normalBuffer
    } else {
        uTargetFraction by 1.0
        uPrevFraction by 0.0
        aPrevVertexPosition by polyContext.target.positionBuffer
        aPrevVertexNormal by polyContext.target.normalBuffer
    }
}