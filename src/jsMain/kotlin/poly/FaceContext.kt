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

class FaceContext(val gl: GL, val polyContext: PolyContext, params: PolyParams) : Param.Context(params)  {
    val poly by { params.targetPoly }
    val animation by { params.transformAnimation }
    val hideFaces by { params.hideFaces.value }
    val selectedFace by { params.selectedFace.value }

    val program = FaceProgram(gl)
    val colorBuffer = program.aVertexColor.createBuffer()
    val prevColorBuffer = program.aPrevVertexColor.createBuffer()
    val faceModeBuffer = program.createUint8Buffer()
    val indexBuffer = program.createUint16Buffer()
    var nIndices = 0
    var hasHiddenFaces = false

    init { setup() }

    override fun update() {
        val animation = animation
        // colors
        updateColor(gl, poly, colorBuffer)
        if (animation != null) updateColor(gl, animation.prevPoly, prevColorBuffer)
        // geometry
        poly.faceVerticesData(faceModeBuffer) { f, _, a, i ->
            a[i] = if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL
        }
        animation?.prevPoly?.faceVerticesData(faceModeBuffer) { f, _, a, i ->
            a[i] = if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL
        }
        faceModeBuffer.bindBufferData(gl)
        // indices
        val indices = indexBuffer.takeData(poly.fs.sumOf { 3 * (it.size - 2) })
        hasHiddenFaces = false // face mode
        nIndices = 0
        var i = 0
        for (f in poly.fs) {
            // Note: In GL front faces are CCW
            if (f.isPlanar && f.kind !in hideFaces) {
                for (k in 2 until f.size) {
                    indices[nIndices++] = i
                    indices[nIndices++] = i + k
                    indices[nIndices++] = i + k - 1
                }
            } else {
                hasHiddenFaces = true
            }
            i += f.size
        }
        gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
        gl.bufferData(GL.ELEMENT_ARRAY_BUFFER, indices, GL.STATIC_DRAW)
    }

}

private fun updateColor(gl: GL, poly: Polyhedron, buffer: Float32Buffer<GLType.vec3>) {
    poly.faceVerticesData(buffer) { f, _, a, i ->
        a.setRGB(i, PolyStyle.faceColor(f))
    }
    buffer.bindBufferData(gl)
}

fun FaceContext.draw(view: ViewContext, lightning: LightningContext) {
    program.use {
        assignView(view)
        assignPolyContext(polyContext)

        uAmbientLightColor by lightning.ambientLightColor
        uDiffuseLightColor by lightning.diffuseLightColor
        uSpecularLightColor by lightning.specularLightColor
        uSpecularLightPower by lightning.specularLightPower
        uLightPosition by lightning.lightPosition

        aVertexColor by colorBuffer
        aPrevVertexColor by if (animation != null) prevColorBuffer else colorBuffer

        aFaceMode by faceModeBuffer
    }
    
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.TRIANGLES, nIndices, GL.UNSIGNED_SHORT, 0)
}

