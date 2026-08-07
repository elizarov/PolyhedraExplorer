/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.plus
import polyhedra.model.util.times
import polyhedra.model.util.unit
import polyhedra.web.glsl.GLType
import polyhedra.web.glsl.createBuffer
import polyhedra.web.glsl.set
import polyhedra.web.glsl.use
import polyhedra.web.main.PolyStyle
import polyhedra.web.params.Param
import polyhedra.web.util.toFloat32Array4
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.khronos.webgl.WebGLRenderingContext as GL

private const val CIRCLE_SEGMENTS = 64

class SymmetryOverlayContext(private val gl: GL, params: RenderParams) : Param.Context(params) {
    private val showSymmetry by { params.poly.showSymmetry.value }
    private val planeNormals by { params.poly.symmetry?.reflectionPlaneNormals.orEmpty() }
    private val axisDirections by { params.poly.symmetry?.rotationAxisDirections.orEmpty() }
    private val radius by { params.poly.targetPoly.circumradius }
    private val planeSize by { params.view.symmetryPlaneSize.value }
    private val axisSize by { params.view.symmetryAxisSize.value }

    private val program = SymmetryOverlayProgram(gl)
    private val planePositionBuffer = createBuffer(gl, GLType.vec3)
    private val axisPositionBuffer = createBuffer(gl, GLType.vec3)
    private val planeColor = PolyStyle.symmetryPlaneColor.toFloat32Array4()
    private val axisColor = PolyStyle.symmetryAxisColor.toFloat32Array4()
    private var planeVertexCount = 0
    private var axisVertexCount = 0

    init { setup() }

    override fun update() {
        if (!showSymmetry) {
            planeVertexCount = 0
            axisVertexCount = 0
            return
        }
        val triangles = symmetryPlaneTriangles(planeNormals, radius * planeSize)
        planeVertexCount = triangles.size
        planePositionBuffer.ensureCapacity(planeVertexCount)
        for (index in triangles.indices) planePositionBuffer[index] = triangles[index]
        planePositionBuffer.bindBufferData(gl)

        val lines = symmetryAxisLines(axisDirections, radius, axisSize)
        axisVertexCount = lines.size
        axisPositionBuffer.ensureCapacity(axisVertexCount)
        for (index in lines.indices) axisPositionBuffer[index] = lines[index]
        axisPositionBuffer.bindBufferData(gl)
    }

    fun draw(view: ViewContext) {
        drawPlanes(view)
        drawAxes(view)
    }

    private fun drawPlanes(view: ViewContext) {
        if (planeVertexCount == 0) return
        program.use {
            assignView(view)
            uColor by planeColor
            aPosition by planePositionBuffer
        }
        gl.depthMask(false)
        gl[GL.BLEND] = true
        gl[GL.CULL_FACE] = false
        gl.drawArrays(GL.TRIANGLES, 0, planeVertexCount)
        gl[GL.CULL_FACE] = true
        gl[GL.BLEND] = false
        gl.depthMask(true)
    }

    private fun drawAxes(view: ViewContext) {
        if (axisVertexCount == 0) return
        program.use {
            assignView(view)
            uColor by axisColor
            aPosition by axisPositionBuffer
        }
        gl.lineWidth(1.0f)
        gl.drawArrays(GL.LINES, 0, axisVertexCount)
    }
}

internal fun symmetryPlaneTriangles(
    normals: List<Vec3>,
    radius: Double,
    segments: Int = CIRCLE_SEGMENTS,
): List<Vec3> = buildList(normals.size * segments * 3) {
    require(radius > 0.0)
    require(segments >= 3)
    for (normal in normals) {
        val reference = if (abs(normal.z) < 0.9) Vec3(0.0, 0.0, 1.0) else Vec3(0.0, 1.0, 0.0)
        val firstAxis = (normal cross reference).unit
        val secondAxis = normal cross firstAxis
        fun circlePoint(segment: Int): Vec3 {
            val angle = 2.0 * PI * segment / segments
            return firstAxis * (radius * cos(angle)) + secondAxis * (radius * sin(angle))
        }
        for (segment in 0 until segments) {
            add(Vec3.ZERO)
            add(circlePoint(segment))
            add(circlePoint((segment + 1) % segments))
        }
    }
}

internal fun symmetryAxisLines(
    directions: List<Vec3>,
    radius: Double,
    size: Double,
): List<Vec3> = buildList(directions.size * 2) {
    require(radius > 0.0)
    require(size > 0.0)
    val halfLength = radius * size
    for (direction in directions) {
        val endpoint = direction.unit * halfLength
        add(endpoint * -1.0)
        add(endpoint)
    }
}
