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

class EdgeContext(val gl: GL, val polyContext: PolyContext, params: PolyParams) : Param.Context(params) {
    val poly by { params.targetPoly }

    val program = EdgeProgram(gl)
    val indexBuffer = program.createUint16Buffer()
    var nIndices = 0
    lateinit var color: Float32Array

    init { setup() }

    override fun update() {
        color = PolyStyle.edgeColor.toFloat32Array4()
        program.use()
        // indices
        val indices = indexBuffer.takeData(poly.es.size * 4)
        nIndices = 0
        var i = 0
        for (f in poly.fs) {
            for (k in 0 until f.size) {
                indices[nIndices++] = i + k
                indices[nIndices++] = i + (k + 1) % f.size
            }
            i += f.size
        }
        gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
        gl.bufferData(GL.ELEMENT_ARRAY_BUFFER, indices, GL.STATIC_DRAW)
    }
}

// cullMode: 0 - no, 1 - cull front, -1 - cull back
fun EdgeContext.draw(view: ViewContext, cullMode: Int = 0) {
    program.use {
        assignView(view)
        assignPolyContext(polyContext)
        uVertexColor by color
        uCullMode by cullMode.toDouble()
    }

    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.LINES, nIndices, GL.UNSIGNED_SHORT, 0)
}
