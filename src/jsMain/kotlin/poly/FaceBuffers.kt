package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceBuffers(val gl: GL) {
    val program = FaceProgram(gl)
    val positionBuffer = program.aVertexPosition.createBuffer()
    val normalBuffer = program.aVertexNormal.createBuffer()
    val colorBuffer = program.aVertexColor.createBuffer()
    val indexBuffer = program.createUint16Buffer()
    var nIndices = 0
}

fun FaceBuffers.draw(viewMatrices: ViewMatrices, lightning: Lightning) {
    program.use {
        assignView(viewMatrices)
        
        uAmbientLightColor.assign(lightning.ambientLightColor)
        uDirectionalLightColor.assign(lightning.directionalLightColor)
        uDirectionalLightVector.assign(lightning.directionalLightVector)

        aVertexPosition.assign(positionBuffer)
        aVertexNormal.assign(normalBuffer)
        aVertexColor.assign(colorBuffer)
    }
    
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.TRIANGLES, nIndices, GL.UNSIGNED_SHORT, 0)
}

fun FaceBuffers.initBuffers(poly: Polyhedron, style: PolyStyle) {
    program.use()
    poly.faceVerticesData(gl, positionBuffer) { _, v, a, i ->
        a[i] = v.pt
    }
    poly.faceVerticesData(gl, normalBuffer) { f, _, a, i ->
        a[i] = f.plane.n
    }
    poly.faceVerticesData(gl, colorBuffer) { f, _, a, i ->
        a[i] = style.faceColor(f)
    }
    // indices
    nIndices = poly.fs.sumOf { 3 * (it.size - 2) }
    val indices = indexBuffer.takeData(nIndices)
    var i = 0
    var j = 0
    for (f in poly.fs) {
        for (k in 2 until f.size) {
            indices[j++] = i
            indices[j++] = i + k - 1
            indices[j++] = i + k
        }
        i += f.size
    }
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.bufferData(GL.ELEMENT_ARRAY_BUFFER, indices, GL.STATIC_DRAW)
}

fun <T : GLType<T, *>> Polyhedron.faceVerticesData(
    gl: GL,
    buffer: Float32Buffer<T>,
    transform: (f: Face, v: Vertex, a: Float32Array, i: Int) -> Unit)
{
    val m = fs.sumOf { it.size }
    val a = buffer.takeData(buffer.type.bufferSize * m)
    var i = 0
    for (f in fs) {
        for (v in f) {
            transform(f, v, a, i)
            i += buffer.type.bufferSize
        }
    }
    gl.bindBuffer(GL.ARRAY_BUFFER, buffer.glBuffer)
    gl.bufferData(GL.ARRAY_BUFFER, a, GL.STATIC_DRAW)
}
