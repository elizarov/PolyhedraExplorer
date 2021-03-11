/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.common.*
import polyhedra.js.glsl.*
import polyhedra.js.params.*
import org.khronos.webgl.WebGLRenderingContext as GL

class PolyContext(val gl: GL, override val params: PolyParams) : Param.Context(Param.UpdateType.TargetValueAndAnimationsList) {
    val target = PolyBuffers(gl)
    val prev = PolyBuffers(gl) // only filled when animation != null
    var animation: TransformAnimation? = null
        private set

    init {
        setupAndUpdate()
    }

    override fun update() {
        val animation = params.transformAnimation
        this.animation = animation
        if (animation != null) {
            target.initBuffers(animation.targetPoly)
            prev.initBuffers(animation.prevPoly)
        } else {
            target.initBuffers(params.poly)
        }
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

