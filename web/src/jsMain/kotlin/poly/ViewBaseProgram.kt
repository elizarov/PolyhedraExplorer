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
    val uCullMode by uniform(GLType.float) // 0 - no, 1 - cull front, -1 - cull back

    val fViewPosition by function(
        GLType.vec4,
        "position", GLType.vec3,
        "expandDir", GLType.vec3
    ) { position, expandDir ->
        // todo: optimize when not expanded?
        uModelMatrix * vec4(position + expandDir * uExpand, 1.0)
    }

    // face direction: > 0 - front-face, < 0 - back-face
    val fFaceDirection by function(
        GLType.float,
        "position", GLType.vec4,
        "normal", GLType.vec3
    ) { position, normal ->
        dot((position.xyz - uCameraPosition), normal)
    }

    val fCullMull by function(
        GLType.float,
        "position", GLType.vec4,
        "normal", GLType.vec3
    ) { position, normal ->
        select(
            uCullMode eq 0.0.literal,
            1.0.literal,
            select(fFaceDirection(position, normal) * uCullMode ge 0.0.literal, 1.0.literal, 0.0.literal)
        )
    }

    fun assignView(view: ViewContext, cullMode: Int = 0) {
        with(view) {
            uCameraPosition by cameraPosition
            uProjectionMatrix by projectionMatrix
            uModelMatrix by modelMatrix
            uNormalMatrix by normalMatrix
            uExpand by expandFaces
            uColorAlpha by 1.0 - transparentFaces
            uFaceWidth by faceWidth
            uFaceRim by faceRim
            uCullMode by cullMode.toDouble()
        }
    }
}