/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.poly.*
import polyhedra.common.util.*
import polyhedra.js.glsl.*
import polyhedra.js.main.*
import polyhedra.js.params.*
import kotlin.math.*
import org.khronos.webgl.WebGLRenderingContext as GL

private const val MAX_RIM_FRACTION = 0.8

class FaceContext(val gl: GL, params: RenderParams) : Param.Context(params)  {
    val poly by { params.poly.targetPoly }
    val animation by { params.poly.transformAnimation }
    val hideFaces by { params.poly.hideFaces.value }
    val selectedFace by { params.poly.selectedFace.value }
    val drawFaces by { params.view.display.value.hasFaces() && params.view.transparentFaces.value < 1.0 }
    val hasExpand by { params.view.expandFaces.value > 0.0 }
    val hasRim by { params.view.faceRim.value > 0.0 }
    val hasWidth by { params.view.faceWidth.value > 0.0 }
    val hasHiddenFaces by {
        poly.fs.any { f -> !f.isPlanar || f.kind in hideFaces }
    }

    val program = FaceProgram(gl)

    var indexSize = 0
    val target = FaceBuffers()
    val prev = FaceBuffers() // only filled when animation != null
    val innerBuffer = createUint8Buffer(gl)
    val faceModeBuffer = createUint8Buffer(gl)
    val indexBuffer = createUint32Buffer(gl)

    init { setup() }

    override fun update() {
        if (!drawFaces) return
        program.use()
        indexSize = target.update(poly, innerBuffer, faceModeBuffer, indexBuffer)
        animation?.let { prev.update(it.prevPoly) }
    }

    inner class FaceBuffers {
        val positionBuffer = createBuffer(gl, GLType.vec3)
        val lightNormalBuffer = createBuffer(gl, GLType.vec3)
        val expandDirBuffer = createBuffer(gl, GLType.vec3)
        val rimDirBuffer = createBuffer(gl, GLType.vec3)
        val rimMaxBuffer = createBuffer(gl, GLType.float)
        val colorBuffer = createBuffer(gl, GLType.vec3)

        fun update(
            poly: Polyhedron,
            innerBuffer: Uint8Buffer? = null,
            faceModeBuffer: Uint8Buffer? = null,
            indexBuffer: Uint32Buffer? = null,
        ): Int {
            var bufferSize = 0
            var indexSize = 0
            for (f in poly.fs) {
                if (f.isPlanar && f.kind !in hideFaces) {
                    bufferSize += f.size
                    indexSize += (f.size - 2) * 3
                    if (hasHiddenFaces || hasExpand) {
                        bufferSize += f.size
                        indexSize += (f.size - 2) * 3
                    }
                } else {
                    if (hasRim) {
                        bufferSize += 2 * 2 * f.size
                        indexSize += 2 * 6 * f.size
                    }
                    if (hasWidth) {
                        bufferSize += 2 * f.size
                        indexSize += 6 * f.size
                    }
                }
                if (hasExpand && hasWidth) {
                    bufferSize += 2 * f.size
                    indexSize += 6 * f.size
                }
            }
            positionBuffer.ensureCapacity(bufferSize)
            lightNormalBuffer.ensureCapacity(bufferSize)
            expandDirBuffer.ensureCapacity(bufferSize)
            rimDirBuffer.ensureCapacity(bufferSize)
            rimMaxBuffer.ensureCapacity(bufferSize)
            colorBuffer.ensureCapacity(bufferSize)
            innerBuffer?.ensureCapacity(bufferSize)
            faceModeBuffer?.ensureCapacity(bufferSize)
            indexBuffer?.ensureCapacity(indexSize)

            var idxOfs = 0
            var bufOfs = 0

            fun Uint32Buffer.indexTriangle(a: Int, b: Int, c: Int, invert: Boolean) {
                this[idxOfs++] = a
                if (invert) {
                    this[idxOfs++] = c
                    this[idxOfs++] = b
                } else {
                    this[idxOfs++] = b
                    this[idxOfs++] = c
                }
            }

            fun makeFace(f: Face, inner: Boolean) {
                val n = f.size
                val faceColor = PolyStyle.faceColor(f)
                var ofs = bufOfs
                val lNorm: Vec3 = if (inner) f * -1.0 else f
                val innerFlag = if (inner) 1 else 0
                for (i in 0 until n) {
                    positionBuffer[ofs] = f[i]
                    lightNormalBuffer[ofs] = lNorm
                    expandDirBuffer[ofs] = f
                    rimDirBuffer[ofs] = Vec3.ZERO
                    rimMaxBuffer[ofs] = 0.0
                    colorBuffer[ofs] = faceColor
                    innerBuffer?.set(ofs, innerFlag)
                    faceModeBuffer?.set(ofs, if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL)
                    ofs++
                }
                if (indexBuffer != null) {
                    for (i in 2 until n) {
                        indexBuffer.indexTriangle(bufOfs, bufOfs + i, bufOfs + i - 1, inner)
                    }
                }
                bufOfs = ofs
            }

            class FaceRimData(val f: Face)  {
                val n = f.size
                val faceColor = PolyStyle.faceColor(f)
                val evs = Array(n) { i ->
                    val j = (i + 1) % n
                    (f[j] - f[i]).unit
                }
                val rimDir = Array(n) { i ->
                    val k = (i + n - 1) % n
                    val a = evs[i]
                    val b = evs[k]
                    val c = 1 - a * b
                    (a - b) / sqrt(2 * c - c * c)
                }
                val maxRim = run {
                    var maxRim = Double.POSITIVE_INFINITY
                    for (i in 0 until n) {
                        val j = (i + 1) % n
                        maxRim = minOf(maxRim, (f[j] - f[i]).norm / (evs[i] * rimDir[i] - evs[i] * rimDir[j]))
                    }
                    maxRim *= MAX_RIM_FRACTION
                    maxRim
                }
            }

            fun Uint32Buffer.indexRectangles(n: Int, invert: Boolean) {
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    indexTriangle(bufOfs + 2 * i, bufOfs + 2 * i + 1, bufOfs + 2 * j, invert)
                    indexTriangle(bufOfs + 2 * i + 1, bufOfs + 2 * j + 1, bufOfs + 2 * j, invert)
                }
            }

            fun makeRim(fr: FaceRimData, inner: Boolean) {
                val f = fr.f
                val n = f.size
                var ofs = bufOfs
                val lNorm = if (inner) -f else f
                val innerFlag = if (inner) 1 else 0
                for (i in 0 until n) {
                    for (rim in 0..1) {
                        positionBuffer[ofs] = f[i]
                        lightNormalBuffer[ofs] = lNorm
                        expandDirBuffer[ofs] = f
                        rimDirBuffer[ofs] = if (rim == 0) Vec3.ZERO else fr.rimDir[i]
                        rimMaxBuffer[ofs] = if (rim == 0) 0.0 else fr.maxRim
                        colorBuffer[ofs] = fr.faceColor
                        innerBuffer?.set(ofs, innerFlag)
                        faceModeBuffer?.set(ofs, if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL)
                        ofs++
                    }
                }
                indexBuffer?.indexRectangles(n, inner)
                bufOfs = ofs
            }

            fun makeBorder(fr: FaceRimData?, f: Face, invert: Boolean) {
                val faceColor = fr?.faceColor ?: PolyStyle.faceColor(f)
                val n = f.size
                var ofs = bufOfs
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    val lNorm = (f[j] cross f[i]).unit.let { if (invert) -it else it }
                    for (innnerFlag in 0..1) {
                        positionBuffer[ofs] = f[i]
                        lightNormalBuffer[ofs] = lNorm
                        expandDirBuffer[ofs] = f
                        rimDirBuffer[ofs] = if (innnerFlag == 0 || fr == null) Vec3.ZERO else fr.rimDir[i]
                        rimMaxBuffer[ofs] = if (innnerFlag == 0 || fr == null) 0.0 else fr.maxRim
                        colorBuffer[ofs] = faceColor
                        innerBuffer?.set(ofs, innnerFlag)
                        faceModeBuffer?.set(ofs, if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL)
                        ofs++
                    }
                }
                indexBuffer?.indexRectangles(f.size, invert)
                bufOfs = ofs
            }

            for (f in poly.fs) {
                // Note: In GL front faces are CCW
                if (f.isPlanar && f.kind !in hideFaces) {
                    makeFace(f, false)
                    if (hasHiddenFaces || hasExpand) makeFace(f, true)
                } else {
                    val fr = FaceRimData(f)
                    if (hasRim) {
                        makeRim(fr, false)
                        makeRim(fr, true)
                    }
                    if (hasWidth) {
                        makeBorder(fr, f, false)
                    }
                }
                if (hasExpand && hasWidth) makeBorder(null, f, true)
            }
            positionBuffer.bindBufferData(gl)
            lightNormalBuffer.bindBufferData(gl)
            expandDirBuffer.bindBufferData(gl)
            rimDirBuffer.bindBufferData(gl)
            rimMaxBuffer.bindBufferData(gl)
            colorBuffer.bindBufferData(gl)
            innerBuffer?.bindBufferData(gl)
            faceModeBuffer?.bindBufferData(gl)
            indexBuffer?.bindBufferData(gl, GL.ELEMENT_ARRAY_BUFFER)
            check(bufOfs == bufferSize)
            if (indexBuffer != null) check(idxOfs == indexSize)
            return indexSize
        }
    }

    fun exportTriangles(
        scale: Double,
        faceWidth: Double,
        faceRim: Double,
        block: (Vec3, Vec3, Vec3) -> Unit
    ) {
        val v1 = MutableVec3()
        val v2 = MutableVec3()
        val v3 = MutableVec3()

        fun MutableVec3.setIndex(i: Int) = with(target) {
            val ri = min(target.rimMaxBuffer.data[i].toDouble(), faceRim)
            val diLen = innerBuffer[i] * faceWidth
            val posLen = norm(
                positionBuffer[i, 0],
                positionBuffer[i, 1],
                positionBuffer[i, 2]
            )
            x = scale * (
                positionBuffer[i, 0] -
                positionBuffer[i, 0] * diLen / posLen +
                rimDirBuffer[i, 0] * ri * (posLen - diLen) / posLen
            )
            y = scale * (
                positionBuffer[i, 1] -
                positionBuffer[i, 1] * diLen / posLen +
                rimDirBuffer[i, 1] * ri * (posLen - diLen) / posLen
            )
            z = scale * (
                positionBuffer[i, 2] -
                positionBuffer[i, 2] * diLen / posLen +
                rimDirBuffer[i, 2] * ri * (posLen - diLen) / posLen
            )
        }

        for (i in 0 until indexSize step 3) {
            v1.setIndex(indexBuffer.data[i])
            v2.setIndex(indexBuffer.data[i + 1])
            v3.setIndex(indexBuffer.data[i + 2])
            block(v1, v2, v3)
        }
    }
}

// cullMode: 0 - no, 1 - cull front, -1 - cull back
fun FaceContext.draw(view: ViewContext, lightning: LightningContext, cullMode: Int = 0) {
    if (!drawFaces) return
    val animation = animation
    val prevOrTarget = if (animation != null) prev else target
    program.use {
        assignView(view, cullMode)

        uAmbientLightColor by lightning.ambientLightColor
        uDiffuseLightColor by lightning.diffuseLightColor
        uSpecularLightColor by lightning.specularLightColor
        uSpecularLightPower by lightning.specularLightPower
        uLightPosition by lightning.lightPosition

        uTargetFraction by (animation?.targetFraction ?: 1.0)
        uPrevFraction by (animation?.prevFraction ?: 0.0)

        aPosition by target.positionBuffer
        aLightNormal by target.lightNormalBuffer
        aExpandDir by target.expandDirBuffer
        aRimDir by target.rimDirBuffer
        aRimMax by target.rimMaxBuffer
        aColor by target.colorBuffer
        aPrevPosition by prevOrTarget.positionBuffer
        aPrevLightNormal by prevOrTarget.lightNormalBuffer
        aPrevExpandDir by prevOrTarget.expandDirBuffer
        aPrevRimDir by prevOrTarget.rimDirBuffer
        aPrevRimMax by prevOrTarget.rimMaxBuffer
        aPrevColor by prevOrTarget.colorBuffer
        aInner by innerBuffer
        aFaceMode by faceModeBuffer
    }
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.TRIANGLES, indexSize, GL.UNSIGNED_INT, 0)
}

