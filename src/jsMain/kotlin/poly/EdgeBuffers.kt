package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

class EdgeBuffers(val gl: GL) {
    val program = EdgeProgram(gl)
    val positionBuffer = program.aVertexPosition.createBuffer()
    val colorBuffer = program.aVertexColor.createBuffer()
    val indexBuffer = program.createUint16Buffer()
    var nIndices = 0
}

fun EdgeBuffers.draw(viewMatrices: ViewMatrices) {
    program.use {
        assignView(viewMatrices)
        aVertexColor.assign(colorBuffer)
        aVertexPosition.assign(positionBuffer)
    }
    
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.LINES, nIndices, GL.UNSIGNED_SHORT, 0)
}

fun EdgeBuffers.initBuffers(poly: Polyhedron, style: PolyStyle) {
    program.use()
    poly.verticesData(gl, positionBuffer) { v, a, i ->
        a[i] = v.pt
    }
    poly.verticesData(gl, colorBuffer) { _, a, i ->
        a[i] = style.edgeColor
    }
    // indices
    nIndices = poly.es.size * 2
    val indices = indexBuffer.takeData(nIndices)
    var j = 0
    for (e in poly.es) {
        indices[j++] = e.a.id
        indices[j++] = e.b.id
    }
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.bufferData(GL.ELEMENT_ARRAY_BUFFER, indices, GL.STATIC_DRAW)
}

fun <T : GLType<T, *>> Polyhedron.verticesData(
    gl: GL,
    buffer: Float32Buffer<T>,
    transform: (v: Vertex, a: Float32Array, i: Int) -> Unit)
{
    val m = vs.size
    val a = buffer.takeData(buffer.type.bufferSize * m)
    var i = 0
    for (v in vs) {
        transform(v, a, i)
        i += buffer.type.bufferSize
    }
    gl.bindBuffer(GL.ARRAY_BUFFER, buffer.glBuffer)
    gl.bufferData(GL.ARRAY_BUFFER, a, GL.STATIC_DRAW)
}
