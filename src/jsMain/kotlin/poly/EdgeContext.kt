/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.poly.*
import polyhedra.js.glsl.*
import polyhedra.js.main.*
import polyhedra.js.params.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

class EdgeContext(val gl: GL, params: PolyParams) : Param.Context(params) {
    val poly by { params.targetPoly }
    val animation by { params.transformAnimation }

    val program = EdgeProgram(gl)

    lateinit var color: Float32Array
    var indexSize = 0
    val indexBuffer = program.createUint16Buffer()
    val target = EdgeBuffers()
    val prev = EdgeBuffers() // only filled when animation != null

    init { setup() }

    override fun update() {
        program.use()
        color = PolyStyle.edgeColor.toFloat32Array4()
        indexSize = target.update(poly, indexBuffer)
        animation?.let { prev.update(it.prevPoly) }
    }

    inner class EdgeBuffers {
        val positionBuffer = createBuffer(gl, GLType.vec3)
        val normalBuffer = createBuffer(gl, GLType.vec3)

        fun update(poly: Polyhedron, indexBuffer: Uint16Buffer? = null): Int {
            val bufferSize = poly.es.size * 2
            val indexSize = poly.es.size * 4
            positionBuffer.ensureCapacity(bufferSize)
            normalBuffer.ensureCapacity(bufferSize)
            indexBuffer?.ensureCapacity(indexSize)
            var bufOfs = 0
            var idxOfs = 0
            for (f in poly.fs) {
                for (k in 0 until f.size) {
                    positionBuffer[bufOfs + k] = f[k]
                    normalBuffer[bufOfs + k] = f
                }
                if (indexBuffer != null) {
                    for (k in 0 until f.size) {
                        indexBuffer[idxOfs++] = bufOfs + k
                        indexBuffer[idxOfs++] = bufOfs + (k + 1) % f.size
                    }
                }
                bufOfs += f.size
            }
            positionBuffer.bindBufferData(gl)
            normalBuffer.bindBufferData(gl)
            indexBuffer?.bindBufferData(gl, GL.ELEMENT_ARRAY_BUFFER)
            check(bufOfs == bufferSize)
            if (indexBuffer != null) check(idxOfs == indexSize)
            return indexSize
        }
    }
}

// cullMode: 0 - no, 1 - cull front, -1 - cull back
fun EdgeContext.draw(view: ViewContext, cullMode: Int = 0) {
    val animation = animation
    val prevOrTarget = if (animation != null) prev else target
    program.use {
        assignView(view)

        uVertexColor by color
        uCullMode by cullMode.toDouble()

        uTargetFraction by (animation?.targetFraction ?: 1.0)
        uPrevFraction by (animation?.prevFraction ?: 0.0)

        aVertexPosition by target.positionBuffer
        aVertexNormal by target.normalBuffer
        aPrevVertexPosition by prevOrTarget.positionBuffer
        aPrevVertexNormal by prevOrTarget.normalBuffer
    }
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.LINES, indexSize, GL.UNSIGNED_SHORT, 0)
}
