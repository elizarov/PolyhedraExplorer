package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

class EdgeBuffers(val gl: GL) {
    val program = EdgeProgram(gl)
    val positionBuffer = program.aVertexPosition.createBuffer()
    val normalBuffer = program.aVertexNormal.createBuffer()
    val indexBuffer = program.createUint16Buffer()
    var nIndices = 0
    lateinit var color: Float32Array
}

fun EdgeBuffers.draw(viewMatrices: ViewMatrices) {
    program.use {
        assignView(viewMatrices)
        uVertexColor.assign(color)
        aVertexPosition.assign(positionBuffer)
        aVertexNormal.assign(normalBuffer)
    }
    
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.LINES, nIndices, GL.UNSIGNED_SHORT, 0)
}

fun EdgeBuffers.initBuffers(poly: Polyhedron, style: PolyStyle) {
    color = style.edgeColor.toFloat32Array()
    program.use()
    poly.faceVerticesData(gl, positionBuffer) { _, v, a, i ->
        a[i] = v.pt
    }
    poly.faceVerticesData(gl, normalBuffer) { f, _, a, i ->
        a[i] = f.plane.n
    }
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
