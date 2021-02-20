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
    val indexBuffer = gl.createBuffer()!!
    var nIndices = 0
}

fun FaceBuffers.draw(viewMatrices: ViewMatrices, lightning: Lightning) {
    program.use {
        uProjectionMatrix.assign(viewMatrices.projectionMatrix)
        uModelViewMatrix.assign(viewMatrices.modelViewMatrix)
        uNormalMatrix.assign(viewMatrices.normalMatrix)

        uAmbientLightColor.assign(lightning.ambientLightColor)
        uDirectionalLightColor.assign(lightning.directionalLightColor)
        uDirectionalLightVector.assign(lightning.directionalLightVector)

        aVertexPosition.assign(positionBuffer)
        aVertexNormal.assign(normalBuffer)
        aVertexColor.assign(colorBuffer)
    }
    
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer)
    gl.drawElements(GL.TRIANGLES, nIndices, GL.UNSIGNED_SHORT, 0)
}

fun GL.enableVertexAttribBuffer(location: Int, buffer: WebGLBuffer, size: Int) {
    bindBuffer(GL.ARRAY_BUFFER, buffer)
    vertexAttribPointer(location, size, GL.FLOAT, false, 0, 0)
    enableVertexAttribArray(location)
}

fun FaceBuffers.initBuffers(poly: Polyhedron, style: PolyStyle) {
    program.use()
    poly.vertexAttribData(gl, positionBuffer) { _, v, a, i ->
        a[i] = v.pt
    }
    poly.vertexAttribData(gl, normalBuffer) { f, _, a, i ->
        a[i] = f.plane.n
    }
    poly.vertexAttribData(gl, colorBuffer) { f, _, a, i ->
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

