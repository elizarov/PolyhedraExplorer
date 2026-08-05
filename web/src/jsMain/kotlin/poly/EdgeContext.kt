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

class EdgeContext(val gl: GL, params: RenderParams) : Param.Context(params) {
    val drawEdges by { params.view.display.value.hasEdges() }
    val poly by { params.poly.targetPoly }
    val animation by { params.poly.transformAnimation }
    val selectedEdge by { params.poly.selectedEdge.value }

    val program = EdgeProgram(gl)

    lateinit var color: Float32Array
    lateinit var selectedColor: Float32Array
    var indexSize = 0
    var selectedIndexSize = 0
    val indexBuffer = createUint16Buffer(gl)
    val selectedIndexBuffer = createUint16Buffer(gl)
    val target = EdgeBuffers()
    val prev = EdgeBuffers() // only filled when animation != null

    init { setup() }

    override fun update() {
        if (!drawEdges && selectedEdge == null) {
            indexSize = 0
            selectedIndexSize = 0
            return
        }
        program.use()
        color = PolyStyle.edgeColor.toFloat32Array4()
        selectedColor = PolyStyle.selectionColor.toFloat32Array4()
        val sizes = target.update(poly, indexBuffer, selectedIndexBuffer, selectedEdge)
        indexSize = sizes.all
        selectedIndexSize = sizes.selected
        animation?.let { prev.update(it.prevPoly) }
    }

    inner class EdgeBuffers {
        val positionBuffer = createBuffer(gl, GLType.vec3)
        val normalBuffer = createBuffer(gl, GLType.vec3)

        internal fun update(
            poly: Polyhedron,
            indexBuffer: Uint16Buffer? = null,
            selectedIndexBuffer: Uint16Buffer? = null,
            selectedEdge: EdgeKind? = null,
        ): EdgeIndexSizes {
            val bufferSize = poly.es.size * 2
            val indexSize = poly.es.size * 4
            positionBuffer.ensureCapacity(bufferSize)
            normalBuffer.ensureCapacity(bufferSize)
            indexBuffer?.ensureCapacity(indexSize)
            selectedIndexBuffer?.ensureCapacity(indexSize)
            var bufOfs = 0
            var idxOfs = 0
            var selectedIdxOfs = 0
            for (f in poly.fs) {
                for (i in 0 until f.size) {
                    positionBuffer[bufOfs + i] = f[i]
                    normalBuffer[bufOfs + i] = f
                }
                if (indexBuffer != null) {
                    for (i in 0 until f.size) {
                        val j = (i + 1) % f.size
                        indexBuffer[idxOfs++] = bufOfs + i
                        indexBuffer[idxOfs++] = bufOfs + j
                        if (f.directedEdgeAt(i).normalizedDirection().kind == selectedEdge) {
                            selectedIndexBuffer?.set(selectedIdxOfs++, bufOfs + i)
                            selectedIndexBuffer?.set(selectedIdxOfs++, bufOfs + j)
                        }
                    }
                }
                bufOfs += f.size
            }
            positionBuffer.bindBufferData(gl)
            normalBuffer.bindBufferData(gl)
            indexBuffer?.bindBufferData(gl, GL.ELEMENT_ARRAY_BUFFER)
            selectedIndexBuffer?.bindBufferData(gl, GL.ELEMENT_ARRAY_BUFFER)
            check(bufOfs == bufferSize)
            if (indexBuffer != null) check(idxOfs == indexSize)
            return EdgeIndexSizes(indexSize, selectedIdxOfs)
        }
    }
}

internal data class EdgeIndexSizes(val all: Int, val selected: Int)

// cullMode: 0 - no, 1 - cull front, -1 - cull back
fun EdgeContext.draw(view: ViewContext, cullMode: Int = 0) {
    if (!drawEdges && selectedIndexSize == 0) return
    val animation = animation
    val prevOrTarget = if (animation != null) prev else target
    program.use {
        assignView(view, cullMode)

        uTargetFraction by (animation?.targetFraction ?: 1.0)
        uPrevFraction by (animation?.prevFraction ?: 0.0)

        aPosition by target.positionBuffer
        aNormal by target.normalBuffer
        aPrevPosition by prevOrTarget.positionBuffer
        aPrevNormal by prevOrTarget.normalBuffer

        if (drawEdges) {
            uVertexColor by color
            gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
            gl.drawElements(GL.LINES, indexSize, GL.UNSIGNED_SHORT, 0)
        }
        if (selectedIndexSize > 0) {
            uVertexColor by selectedColor
            gl.lineWidth(2.0f)
            gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, selectedIndexBuffer.glBuffer)
            gl.drawElements(GL.LINES, selectedIndexSize, GL.UNSIGNED_SHORT, 0)
            gl.lineWidth(1.0f)
        }
    }
}
