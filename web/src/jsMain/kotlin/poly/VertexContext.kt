/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import polyhedra.model.poly.Polyhedron
import polyhedra.model.util.Vec3
import polyhedra.model.util.plus
import polyhedra.model.util.times
import polyhedra.web.glsl.*
import polyhedra.web.main.PolyStyle
import polyhedra.web.params.Param
import polyhedra.web.util.toFloat32Array4
import kotlin.math.sqrt
import org.khronos.webgl.WebGLRenderingContext as GL

private const val VERTEX_BALL_RADIUS = 0.025

class VertexContext(val gl: GL, params: RenderParams) : Param.Context(params) {
    val poly by { params.poly.targetPoly }
    val animation by { params.poly.transformAnimation }
    val selectedVertex by { params.poly.selectedVertex.value }

    private val program = VertexProgram(gl)
    private val target = VertexBuffers()
    private val prev = VertexBuffers()
    private val indexBuffer = createUint32Buffer(gl)
    private val color = PolyStyle.selectionColor.toFloat32Array4()
    private var indexSize = 0

    init { setup() }

    override fun update() {
        val selected = selectedVertex
        if (selected == null) {
            indexSize = 0
            return
        }
        val vertexIds = poly.vs.filter { it.kind == selected }.map { it.id }
        indexSize = target.update(poly, vertexIds, indexBuffer)
        animation?.let { prev.update(it.prevPoly, vertexIds) }
    }

    private inner class VertexBuffers {
        val positionBuffer = createBuffer(gl, GLType.vec3)
        val normalBuffer = createBuffer(gl, GLType.vec3)

        fun update(poly: Polyhedron, vertexIds: List<Int>, indexBuffer: Uint32Buffer? = null): Int {
            val vertexCount = vertexIds.size * sphereDirections.size
            val indexCount = vertexIds.size * sphereIndices.size
            positionBuffer.ensureCapacity(vertexCount)
            normalBuffer.ensureCapacity(vertexCount)
            indexBuffer?.ensureCapacity(indexCount)
            val radius = poly.circumradius * VERTEX_BALL_RADIUS
            var vertexOffset = 0
            var indexOffset = 0
            for (vertexId in vertexIds) {
                val center = poly.vs[vertexId]
                for (direction in sphereDirections) {
                    positionBuffer[vertexOffset] = center + direction * radius
                    normalBuffer[vertexOffset] = direction
                    vertexOffset++
                }
                if (indexBuffer != null) {
                    val sphereStart = vertexOffset - sphereDirections.size
                    for (index in sphereIndices) indexBuffer[indexOffset++] = sphereStart + index
                }
            }
            positionBuffer.bindBufferData(gl)
            normalBuffer.bindBufferData(gl)
            indexBuffer?.bindBufferData(gl, GL.ELEMENT_ARRAY_BUFFER)
            return indexCount
        }
    }

    fun draw(view: ViewContext) {
        if (indexSize == 0) return
        val animation = animation
        val previous = if (animation == null) target else prev
        program.use {
            assignView(view)
            uVertexColor by color
            uTargetFraction by (animation?.targetFraction ?: 1.0)
            uPrevFraction by (animation?.prevFraction ?: 0.0)
            aPosition by target.positionBuffer
            aNormal by target.normalBuffer
            aPrevPosition by previous.positionBuffer
        }
        gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
        gl.drawElements(GL.TRIANGLES, indexSize, GL.UNSIGNED_INT, 0)
    }
}

private val sphereDirections: List<Vec3> = run {
    val phi = (1.0 + sqrt(5.0)) / 2.0
    val scale = 1.0 / sqrt(1.0 + phi * phi)
    listOf(
        Vec3(-1.0, phi, 0.0), Vec3(1.0, phi, 0.0),
        Vec3(-1.0, -phi, 0.0), Vec3(1.0, -phi, 0.0),
        Vec3(0.0, -1.0, phi), Vec3(0.0, 1.0, phi),
        Vec3(0.0, -1.0, -phi), Vec3(0.0, 1.0, -phi),
        Vec3(phi, 0.0, -1.0), Vec3(phi, 0.0, 1.0),
        Vec3(-phi, 0.0, -1.0), Vec3(-phi, 0.0, 1.0),
    ).map { it * scale }
}

private val sphereIndices = intArrayOf(
    0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11,
    1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
    3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9,
    4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1,
)
