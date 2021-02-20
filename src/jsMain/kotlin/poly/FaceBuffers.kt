package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceBuffers(val gl: GL, val sharedPolyBuffers: SharedPolyBuffers)  {
    val program = FaceProgram(gl)
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

        assignSharedPolyBuffers(sharedPolyBuffers)
        aVertexColor.assign(colorBuffer)
    }
    
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.TRIANGLES, nIndices, GL.UNSIGNED_SHORT, 0)
}

fun FaceBuffers.initBuffers(poly: Polyhedron, style: PolyStyle) {
    poly.faceVerticesData(colorBuffer) { f, _, a, i ->
        a[i] = style.faceColor(f)
    }
    colorBuffer.bindBufferData(gl)
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

