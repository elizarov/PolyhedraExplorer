package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceBuffers(val gl: GL) {
    val program = FaceProgram(gl)
    val positionBuffer = gl.createBuffer()!!
    val normalBuffer = gl.createBuffer()!!
    val colorBuffer = gl.createBuffer()!!
    val indexBuffer = gl.createBuffer()
    var nIndices = 0
}

fun FaceBuffers.draw(viewMatrices: ViewMatrices, lightning: Lightning) {
    program.useProgram()
    gl.uniformMatrix4fv(program.uProjectionMatrix.location, false, viewMatrices.projectionMatrix)
    gl.uniformMatrix4fv(program.uModelViewMatrix.location, false, viewMatrices.modelViewMatrix)
    gl.uniformMatrix3fv(program.uNormalMatrix.location, false, viewMatrices.normalMatrix)

    gl.uniform3fv(program.uAmbientLightColor.location, lightning.ambientLightColor)
    gl.uniform3fv(program.uDirectionalLightColor.location, lightning.directionalLightColor)
    gl.uniform3fv(program.uDirectionalLightVector.location, lightning.directionalLightVector)

    gl.enableVertexAttribBuffer(program.aVertexPosition.location, positionBuffer, 3)
    gl.enableVertexAttribBuffer(program.aVertexNormal.location, normalBuffer, 3)
    gl.enableVertexAttribBuffer(program.aVertexColor.location, colorBuffer, 4)
    
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer)
    gl.drawElements(GL.TRIANGLES, nIndices, GL.UNSIGNED_SHORT, 0)
}

fun GL.enableVertexAttribBuffer(location: Int, buffer: WebGLBuffer, size: Int) {
    bindBuffer(GL.ARRAY_BUFFER, buffer)
    vertexAttribPointer(location, size, GL.FLOAT, false, 0, 0)
    enableVertexAttribArray(location)
}

fun FaceBuffers.initBuffers(poly: Polyhedron, style: PolyStyle) {
    program.useProgram()
    poly.vertexAttribData(gl, positionBuffer, 3) { _, v, a, i ->
        a[i] = v.pt
    }
    poly.vertexAttribData(gl, normalBuffer,3) { f, _, a, i ->
        a[i] = f.plane.n
    }
    poly.vertexAttribData(gl, colorBuffer, 4) { f, _, a, i ->
        a[i] = style.faceColor(f)
    }
    // indices
    nIndices = poly.fs.sumOf { 3 * (it.size - 2) }
    val indices = Uint16Array(nIndices)
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
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer)
    gl.bufferData(GL.ELEMENT_ARRAY_BUFFER, indices, GL.STATIC_DRAW)
}

fun Polyhedron.vertexAttribData(
    gl: GL,
    buffer: WebGLBuffer,
    size: Int,
    transform: (f: Face, v: Vertex, a: Float32Array, i: Int) -> Unit)
{
    val data = vertexArray(size, ::Float32Array, transform)
    gl.bindBuffer(GL.ARRAY_BUFFER, buffer)
    gl.bufferData(GL.ARRAY_BUFFER, data, GL.STATIC_DRAW)
}

fun <A> Polyhedron.vertexArray(
    size: Int,
    factory: (Int) -> A,
    transform: (f: Face, v: Vertex, a: A, i: Int) -> Unit
): A {
    val m = fs.sumOf { it.size }
    val a = factory(size * m)
    var i = 0
    for (f in fs) {
        for (v in f) {
            transform(f, v, a, i)
            i += size
        }
    }
    return a
}