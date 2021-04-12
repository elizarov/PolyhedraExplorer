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
import polyhedra.js.util.*
import kotlin.math.*
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceContext(val gl: GL, params: RenderParams) : Param.Context(params)  {
    val poly by { params.poly.targetPoly }
    val animation by { params.poly.transformAnimation }
    val selectedFace by { params.poly.selectedFace.value }
    val drawFaces by { params.view.display.value.hasFaces() && params.view.transparentFaces.value < 1.0 }
    val hasExpand by { params.view.expandFaces.value > 0.0 }
    val hasRim by { params.view.faceRim.value > 0.0 }
    val hasWidth by { params.view.faceWidth.value > 0.0 }
    
    // effectively hidden faces
    val hiddenFaces by {
        val animation = animation
        // note: poly == animation.targetPoly
        val hf = params.poly.hideFaces.value.intersect(poly.faceKinds.keys) + poly.nonPlanarFaceKinds
        if (animation == null) hf else hf + animation.prevPoly.nonPlanarFaceKinds
    }

    val program = FaceProgram(gl)

    var indexSize = 0
    var bufferSize = 0
    val target = FaceBuffers()
    val prev = FaceBuffers() // only filled when animation != null
    val innerBuffer = createUint8Buffer(gl)
    val faceModeBuffer = createUint8Buffer(gl)
    val indexBuffer = createUint32Buffer(gl)

    init { setup() }

    override fun update() {
        if (!drawFaces) return
        program.use()
        val altFaceKind = animation?.let {
            { f: Face -> it.prevPoly.fs[f.id].kind }
        }
        indexSize = target.update(poly, altFaceKind, innerBuffer, faceModeBuffer, indexBuffer)
        animation?.let {
            val prevIndexSize = prev.update(it.prevPoly, { f -> poly.fs[f.id].kind })
            check(prevIndexSize == indexSize)
        }
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
            altFaceKind: ((Face) -> FaceKind)?,
            innerBuffer: Uint8Buffer? = null,
            faceModeBuffer: Uint8Buffer? = null,
            indexBuffer: Uint32Buffer? = null,
        ): Int {
            fun faceShown(f: Face): Boolean =
                f.kind !in hiddenFaces && (altFaceKind == null || altFaceKind(f) !in hiddenFaces)

            var bufferSize = 0
            var indexSize = 0
            val hasHiddenFaces = hiddenFaces.isNotEmpty()
            for (f in poly.fs) {
                if (faceShown(f)) {
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

            fun makeFace(f: Face, faceColor: Color, inner: Boolean) {
                val n = f.size
                var ofs = bufOfs
                val lNorm: Vec3 = if (inner) -f else f
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

            fun Uint32Buffer.indexRectangles(n: Int, invert: Boolean) {
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    indexTriangle(bufOfs + 2 * i, bufOfs + 2 * i + 1, bufOfs + 2 * j, invert)
                    indexTriangle(bufOfs + 2 * i + 1, bufOfs + 2 * j + 1, bufOfs + 2 * j, invert)
                }
            }

            fun makeRim(f: Face, fr: FaceRim, faceColor: Color, inner: Boolean) {
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
                        colorBuffer[ofs] = faceColor
                        innerBuffer?.set(ofs, innerFlag)
                        faceModeBuffer?.set(ofs, if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL)
                        ofs++
                    }
                }
                indexBuffer?.indexRectangles(n, inner)
                bufOfs = ofs
            }

            fun makeBorder(f: Face, fr: FaceRim, faceColor: Color, noRim: Boolean) {
                val n = f.size
                var ofs = bufOfs
                for (i in 0 until n) {
                    val lNorm = if (noRim) -fr.borderNorm[i] else fr.borderNorm[i]
                    for (innerFlag in 0..1) {
                        positionBuffer[ofs] = f[i]
                        lightNormalBuffer[ofs] = lNorm
                        expandDirBuffer[ofs] = f
                        rimDirBuffer[ofs] = if (noRim) Vec3.ZERO else fr.rimDir[i]
                        rimMaxBuffer[ofs] = if (noRim) 0.0 else fr.maxRim
                        colorBuffer[ofs] = faceColor
                        innerBuffer?.set(ofs, innerFlag)
                        faceModeBuffer?.set(ofs, if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL)
                        ofs++
                    }
                }
                indexBuffer?.indexRectangles(f.size, noRim)
                bufOfs = ofs
            }

            for (f in poly.fs) {
                val faceColor = PolyStyle.faceColor(f)
                // Note: In GL front faces are CCW
                if (faceShown(f)) {
                    makeFace(f, faceColor,false)
                    if (hasHiddenFaces || hasExpand) {
                        makeFace(f, faceColor, true)
                    }
                } else {
                    if (hasRim) {
                        makeRim(f, poly.faceRim(f), faceColor, false)
                        makeRim(f, poly.faceRim(f), faceColor, true)
                    }
                    if (hasWidth) {
                        makeBorder(f, poly.faceRim(f), faceColor, false)
                    }
                }
                if (hasExpand && hasWidth) {
                    makeBorder(f, poly.faceRim(f), faceColor, true)
                }
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
            this@FaceContext.bufferSize = bufferSize
            return indexSize
        }
    }

    private fun FaceExportParams.setIndex(v: MutableVec3, i: Int) = with(target) {
        val ri = min(target.rimMaxBuffer.data[i].toDouble(), rim)
        val diLen = innerBuffer[i] * width
        val posLen = norm(
            positionBuffer[i, 0],
            positionBuffer[i, 1],
            positionBuffer[i, 2]
        )
        fun coord(j: Int) = scale * (
            positionBuffer[i, j] -
            positionBuffer[i, j] * diLen / posLen +
            rimDirBuffer[i, j] * ri * (posLen - diLen) / posLen +
            expandDirBuffer[i, j] * expand
        )
        v.x = coord(0)
        v.y = coord(1)
        v.z = coord(2)
    }

    fun exportVertices(
        exportParams: FaceExportParams,
        block: (Vec3) -> Unit
    ) = with(exportParams) {
        val v = MutableVec3()
        for (i in 0 until bufferSize) {           
            setIndex(v, i)
            block(v)
        }
    }

    fun exportTriangles(
        exportParams: FaceExportParams,
        block: (Vec3, Vec3, Vec3) -> Unit
    ) = with(exportParams) {
        val v1 = MutableVec3()
        val v2 = MutableVec3()
        val v3 = MutableVec3()
        for (i in 0 until indexSize step 3) {
            setIndex(v1, indexBuffer[i])
            setIndex(v2, indexBuffer[i + 1])
            setIndex(v3, indexBuffer[i + 2])
            block(v1, v2, v3)
        }
    }
}

data class FaceExportParams(
    val scale: Double,
    val width: Double,
    val rim: Double,
    val expand: Double
)

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

