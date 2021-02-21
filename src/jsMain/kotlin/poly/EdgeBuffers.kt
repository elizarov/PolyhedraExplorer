package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.glsl.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

class EdgeBuffers(val gl: GL, val sharedPolyBuffers: SharedPolyBuffers) {
    val program = EdgeProgram(gl)
    val indexBuffer = program.createUint16Buffer()
    var nIndices = 0
    lateinit var color: Float32Array
}

fun EdgeBuffers.draw(view: ViewContext) {
    program.use {
        assignView(view)
        assignSharedPolyBuffers(sharedPolyBuffers)
        uVertexColor.assign(color)
    }

    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.LINES, nIndices, GL.UNSIGNED_SHORT, 0)
}

fun EdgeBuffers.initBuffers(poly: Polyhedron) {
    color = PolyStyle.edgeColor.toFloat32Array()
    program.use()
    // indices
    nIndices = poly.es.size * 4
    val indices = indexBuffer.takeData(nIndices)
    var i = 0
    var j = 0
    for (f in poly.fs) {
        for (k in 0 until f.size) {
            indices[j++] = i + k
            indices[j++] = i + (k + 1) % f.size
        }
        i += f.size
    }
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.bufferData(GL.ELEMENT_ARRAY_BUFFER, indices, GL.STATIC_DRAW)
}