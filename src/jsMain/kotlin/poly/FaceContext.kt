/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.poly.*
import polyhedra.js.glsl.*
import polyhedra.js.main.*
import polyhedra.js.params.*
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceContext(val gl: GL, params: PolyParams) : Param.Context(params)  {
    val poly by { params.targetPoly }
    val animation by { params.transformAnimation }
    val hideFaces by { params.hideFaces.value }
    val selectedFace by { params.selectedFace.value }

    val program = FaceProgram(gl)

    var indexSize = 0
    var hasHiddenFaces = false
    val indexBuffer = program.createUint32Buffer()
    val faceModeBuffer = program.createUint8Buffer()
    val target = FaceBuffers()
    val prev = FaceBuffers() // only filled when animation != null

    init { setup() }

    override fun update() {
        program.use()
        indexSize = target.update(poly, indexBuffer, faceModeBuffer)
        animation?.let { prev.update(it.prevPoly) }
    }

    inner class FaceBuffers {
        val positionBuffer = createBuffer(gl, GLType.vec3)
        val normalBuffer = createBuffer(gl, GLType.vec3)
        val colorBuffer = createBuffer(gl, GLType.vec3)

        fun update(poly: Polyhedron, indexBuffer: Uint32Buffer? = null, faceModeBuffer: Uint8Buffer? = null): Int {
            var bufferSize = 0
            var indexSize = 0
            for (f in poly.fs) {
                if (f.isPlanar && f.kind !in hideFaces) {
                    bufferSize += f.size
                    indexSize += (f.size - 2) * 3
                }
            }
            positionBuffer.ensureCapacity(bufferSize)
            normalBuffer.ensureCapacity(bufferSize)
            colorBuffer.ensureCapacity(bufferSize)
            faceModeBuffer?.ensureCapacity(bufferSize)
            indexBuffer?.ensureCapacity(indexSize)
            var idxOfs = 0
            var bufOfs = 0
            for (f in poly.fs) {
                // Note: In GL front faces are CCW
                if (f.isPlanar && f.kind !in hideFaces) {
                    val faceColor = PolyStyle.faceColor(f)
                    for (k in 0 until f.size) {
                        positionBuffer[bufOfs + k] = f[k]
                        normalBuffer[bufOfs + k] = f
                        colorBuffer[bufOfs + k] = faceColor
                        faceModeBuffer?.set(bufOfs + k, if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL)
                    }
                    if (indexBuffer != null) {
                        for (k in 2 until f.size) {
                            indexBuffer[idxOfs++] = bufOfs
                            indexBuffer[idxOfs++] = bufOfs + k
                            indexBuffer[idxOfs++] = bufOfs + k - 1
                        }
                    }
                    bufOfs += f.size
                } else {
                    hasHiddenFaces = true
                }
            }
            positionBuffer.bindBufferData(gl)
            normalBuffer.bindBufferData(gl)
            colorBuffer.bindBufferData(gl)
            faceModeBuffer?.bindBufferData(gl)
            indexBuffer?.bindBufferData(gl, GL.ELEMENT_ARRAY_BUFFER)
            check(bufOfs == bufferSize)
            if (indexBuffer != null) check(idxOfs == indexSize)
            return indexSize
        }
    }
}

fun FaceContext.draw(view: ViewContext, lightning: LightningContext) {
    val animation = animation
    val prevOrTarget = if (animation != null) prev else target
    program.use {
        assignView(view)

        uAmbientLightColor by lightning.ambientLightColor
        uDiffuseLightColor by lightning.diffuseLightColor
        uSpecularLightColor by lightning.specularLightColor
        uSpecularLightPower by lightning.specularLightPower
        uLightPosition by lightning.lightPosition

        uTargetFraction by (animation?.targetFraction ?: 1.0)
        uPrevFraction by (animation?.prevFraction ?: 0.0)

        aVertexPosition by target.positionBuffer
        aVertexNormal by target.normalBuffer
        aVertexColor by target.colorBuffer
        aPrevVertexPosition by prevOrTarget.positionBuffer
        aPrevVertexNormal by prevOrTarget.normalBuffer
        aPrevVertexColor by prevOrTarget.colorBuffer
        aFaceMode by faceModeBuffer
    }
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.TRIANGLES, indexSize, GL.UNSIGNED_INT, 0)
}

