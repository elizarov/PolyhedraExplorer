/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.common.poly.*
import polyhedra.js.glsl.*
import polyhedra.js.params.*
import org.khronos.webgl.WebGLRenderingContext as GL

class PolyContext(val gl: GL, params: PolyParams) : Param.Context(params) {
    val poly by { params.targetPoly }
    val animation by { params.transformAnimation }

    val target = PolyBuffers(gl)
    val prev = PolyBuffers(gl) // only filled when animation != null

    init { setup() }

    override fun update() {
        val animation = animation
        target.initBuffers(poly)
        if (animation != null) prev.initBuffers(animation.prevPoly)
    }
}

class PolyBuffers(val gl: GL) {
    val positionBuffer = createBuffer(gl, GLType.vec3)
    val normalBuffer = createBuffer(gl, GLType.vec3)
}

private fun PolyBuffers.initBuffers(poly: Polyhedron) {
    poly.faceVerticesData(positionBuffer) { _, v, a, i ->
        a[i] = v
    }
    positionBuffer.bindBufferData(gl)

    poly.faceVerticesData(normalBuffer) { f, _, a, i ->
        a[i] = f
    }
    normalBuffer.bindBufferData(gl)
}

