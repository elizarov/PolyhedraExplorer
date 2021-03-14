/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.common.poly.*
import polyhedra.js.glsl.*
import polyhedra.js.main.*
import polyhedra.js.params.*
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceContext(val gl: GL, val polyContext: PolyContext, override val params: PolyParams) : Param.Context()  {
    val program = FaceProgram(gl)
    val colorBuffer = program.aVertexColor.createBuffer()
    val prevColorBuffer = program.aPrevVertexColor.createBuffer()
    val faceModeBuffer = program.createUint8Buffer()
    val indexBuffer = program.createUint16Buffer()
    var nIndices = 0
    var hasHiddenFaces = false

    init { setup() }

    override fun update() {
        val poly = params.targetPoly
        val animation = params.transformAnimation
        if (animation != null) {
            updateColor(gl, animation.targetPoly, colorBuffer, animation.target.dual)
            updateColor(gl, animation.prevPoly, prevColorBuffer, animation.prev.dual)
        } else {
            // simple case without animation
            updateColor(gl, poly, colorBuffer)
        }
        // face mode
        hasHiddenFaces = false
        poly.faceVerticesData(faceModeBuffer) { f, _, a, i ->
            a[i] = FACE_SHOWN
            if (!f.isPlanar) {
                a[i] = FACE_HIDDEN
                hasHiddenFaces = true
            }
        }
        animation?.prevPoly?.faceVerticesData(faceModeBuffer) { f, _, a, i ->
            if (!f.isPlanar) {
                a[i] = FACE_HIDDEN
                hasHiddenFaces = true
            }
        }
        faceModeBuffer.bindBufferData(gl)
        // indices
        nIndices = poly.fs.sumOf { 3 * (it.size - 2) }
        val indices = indexBuffer.takeData(nIndices)
        var i = 0
        var j = 0
        for (f in poly.fs) {
            // Note: In GL front faces are CCW
            for (k in 2 until f.size) {
                indices[j++] = i
                indices[j++] = i + k
                indices[j++] = i + k - 1
            }
            i += f.size
        }
        gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
        gl.bufferData(GL.ELEMENT_ARRAY_BUFFER, indices, GL.STATIC_DRAW)
    }

}

private fun updateColor(gl: GL, poly: Polyhedron, buffer: Float32Buffer<GLType.vec3>, dual: Boolean = false) {
    poly.faceVerticesData(buffer) { f, _, a, i ->
        a.setRGB(i, PolyStyle.faceColor(f, dual))
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
        aPrevVertexColor by if (params.transformAnimation != null) prevColorBuffer else colorBuffer

        aFaceMode by faceModeBuffer
    }
    
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.TRIANGLES, nIndices, GL.UNSIGNED_SHORT, 0)
}

