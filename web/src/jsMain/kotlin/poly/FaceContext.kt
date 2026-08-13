/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import org.khronos.webgl.*
import polyhedra.model.api.CoreStlPresentation
import polyhedra.model.api.CoreStlRequest
import polyhedra.model.poly.*
import polyhedra.model.util.*
import polyhedra.web.glsl.*
import polyhedra.web.main.*
import polyhedra.web.params.*
import polyhedra.web.util.*
import kotlin.math.*
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceContext(
    val gl: GL,
    params: RenderParams,
    private val polyProvider: () -> Polyhedron = { params.poly.targetPoly },
) : Param.Context(params)  {
    val poly by { polyProvider() }
    val animation by { params.poly.transformAnimation }
    val selectedFace by { params.poly.selectedFace.value }
    val drawFaces by { params.view.display.value.hasFaces() && params.view.transparentFaces.value < 1.0 }
    val hasExpand by { params.view.expandFaces.value > 0.0 }
    val hasRim by { params.view.faceRim.value > 0.0 }
    val hasWidth by { params.view.faceWidth.value > 0.0 }
    val printPreview by { params.printPreview.enabled.value }
    val printLightness by { params.printPreview.lightness.value }
    val printChroma by { params.printPreview.chroma.value }
    val printHue by { params.printPreview.hue.value }
    val resolvedRims by { params.poly.resolvedRims }
    
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
    private val targetSurfaceIds = arrayListOf<Int>()

    init { setup() }

    override fun update() {
        if (!drawFaces) return
        program.use()
        val altFaceKind = animation?.let {
            { f: Face -> it.prevPoly.fs[f.id].kind }
        }
        targetSurfaceIds.clear()
        indexSize = target.update(
            poly,
            altFaceKind,
            innerBuffer,
            faceModeBuffer,
            indexBuffer,
            surfaceIds = targetSurfaceIds,
        )
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
            exportParams: FaceExportParams? = null,
            surfaceIds: MutableList<Int>? = null,
        ): Int {
            fun faceShown(f: Face): Boolean =
                f.kind !in hiddenFaces && (altFaceKind == null || altFaceKind(f) !in hiddenFaces)

            val includeExpand = exportParams?.let { it.expand > 0.0 } ?: hasExpand
            val includeRim = exportParams?.let { it.rim > 0.0 } ?: hasRim
            val includeWidth = exportParams?.let { it.width > 0.0 } ?: hasWidth
            val hasHiddenFaces = hiddenFaces.isNotEmpty()

            var bufferSize = 0
            var indexSize = 0
            val resolvedRimByFace = if (animation == null) {
                resolvedRims.associateBy { rim -> rim.sourceFaceId }
            } else {
                emptyMap()
            }
            val rimMeshesByFace = resolvedRimByFace.mapValues { (faceId, rim) ->
                val face = poly.fs[faceId]
                rim.regions.map { region -> region.triangulate(face) }
            }
            for (f in poly.fs) {
                val resolved = poly.resolvedFaces[f.id]
                if (faceShown(f)) {
                    bufferSize += resolved.vertices.size
                    indexSize += resolved.triangles.size * 3
                    if (hasHiddenFaces || includeExpand) {
                        bufferSize += resolved.vertices.size
                        indexSize += resolved.triangles.size * 3
                    }
                } else {
                    val rimMeshes = rimMeshesByFace[f.id]
                    if (rimMeshes != null && includeRim) {
                        val rimVertexCount = rimMeshes.sumOf { mesh -> mesh.vertices.size }
                        val rimIndexCount = rimMeshes.sumOf { mesh -> mesh.triangles.size }
                        bufferSize += 2 * rimVertexCount
                        indexSize += 2 * rimIndexCount
                        if (includeWidth) {
                            val boundarySegmentCount = rimMeshes.sumOf { mesh ->
                                mesh.cycles.sumOf { cycle ->
                                    cycle.vertices.indices.count { index ->
                                        !mesh.triangulationPatch || cycle.segmentSources[index].isNotEmpty()
                                    }
                                }
                            }
                            bufferSize += rimMeshes.sumOf { mesh ->
                                mesh.cycles.sumOf { cycle ->
                                    if (mesh.triangulationPatch) {
                                        4 * cycle.segmentSources.count { sources -> sources.isNotEmpty() }
                                    } else {
                                        2 * cycle.vertices.size
                                    }
                                }
                            }
                            indexSize += 6 * boundarySegmentCount
                        }
                    } else {
                        if (includeRim) {
                            bufferSize += 2 * 2 * f.size
                            indexSize += 2 * 6 * f.size
                        }
                        if (includeWidth) {
                            bufferSize += 2 * f.size
                            indexSize += 6 * f.size
                        }
                    }
                }
                if (includeExpand && includeWidth) {
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
            var nextSurfaceId = 0
            val printColor = if (printPreview) oklchColor(printLightness, printChroma, printHue) else null

            fun Uint32Buffer.indexTriangle(a: Int, b: Int, c: Int, invert: Boolean, surfaceId: Int) {
                this[idxOfs++] = a
                if (invert) {
                    this[idxOfs++] = c
                    this[idxOfs++] = b
                } else {
                    this[idxOfs++] = b
                    this[idxOfs++] = c
                }
                surfaceIds?.add(surfaceId)
            }

            fun makeFace(
                f: Face,
                resolved: ResolvedFaceGeometry,
                faceColor: Color,
                inner: Boolean,
            ) {
                val n = resolved.vertices.size
                var ofs = bufOfs
                val lNorm: Vec3 = if (inner) -f else f
                val innerFlag = if (inner) 1 else 0
                for (i in 0 until n) {
                    positionBuffer[ofs] = resolved.vertices[i].position
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
                    val surfaceId = nextSurfaceId++
                    for (triangle in resolved.triangles) {
                        indexBuffer.indexTriangle(
                            bufOfs + triangle.a,
                            bufOfs + triangle.b,
                            bufOfs + triangle.c,
                            inner,
                            surfaceId,
                        )
                    }
                }
                bufOfs = ofs
            }

            fun Uint32Buffer.indexRectangles(n: Int, invert: Boolean, separateSurfaces: Boolean) {
                val commonSurfaceId = if (separateSurfaces) -1 else nextSurfaceId++
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    val surfaceId = if (separateSurfaces) nextSurfaceId++ else commonSurfaceId
                    indexTriangle(bufOfs + 2 * i, bufOfs + 2 * i + 1, bufOfs + 2 * j, invert, surfaceId)
                    indexTriangle(bufOfs + 2 * i + 1, bufOfs + 2 * j + 1, bufOfs + 2 * j, invert, surfaceId)
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
                indexBuffer?.indexRectangles(n, inner, separateSurfaces = false)
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
                indexBuffer?.indexRectangles(f.size, noRim, separateSurfaces = true)
                bufOfs = ofs
            }

            fun makeResolvedRim(
                f: Face,
                meshes: List<TriangulatedRimRegion>,
                faceColor: Color,
                inner: Boolean,
            ) {
                val innerFlag = if (inner) 1 else 0
                for (mesh in meshes) {
                    val lNorm = if (inner) -mesh.normal else mesh.normal
                    val base = bufOfs
                    val surfaceId = nextSurfaceId++
                    for (position in mesh.vertices) {
                        positionBuffer[bufOfs] = position
                        lightNormalBuffer[bufOfs] = lNorm
                        expandDirBuffer[bufOfs] = f
                        rimDirBuffer[bufOfs] = Vec3.ZERO
                        rimMaxBuffer[bufOfs] = 0.0
                        colorBuffer[bufOfs] = faceColor
                        innerBuffer?.set(bufOfs, innerFlag)
                        faceModeBuffer?.set(bufOfs, if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL)
                        bufOfs++
                    }
                    if (indexBuffer != null) for (triangle in mesh.triangles.indices step 3) {
                        val a = mesh.triangles[triangle]
                        val b = mesh.triangles[triangle + 1]
                        val c = mesh.triangles[triangle + 2]
                        val reversed = ((mesh.vertices[b] - mesh.vertices[a]) cross
                            (mesh.vertices[c] - mesh.vertices[a])) * mesh.normal < 0.0
                        indexBuffer.indexTriangle(base + a, base + b, base + c, inner xor reversed, surfaceId)
                    }
                }
            }

            fun makeResolvedRimBorders(
                f: Face,
                meshes: List<TriangulatedRimRegion>,
                faceColor: Color,
            ) {
                for (mesh in meshes) for (cycle in mesh.cycles) {
                    if (!mesh.triangulationPatch) {
                        val base = bufOfs
                        for (index in cycle.vertices.indices) {
                            val position = cycle.vertices[index]
                            val next = cycle.vertices[(index + 1) % cycle.vertices.size]
                            val sideNormal = (next cross position).unit
                            for (innerFlag in 0..1) {
                                positionBuffer[bufOfs] = position
                                lightNormalBuffer[bufOfs] = sideNormal
                                expandDirBuffer[bufOfs] = f
                                rimDirBuffer[bufOfs] = Vec3.ZERO
                                rimMaxBuffer[bufOfs] = 0.0
                                colorBuffer[bufOfs] = faceColor
                                innerBuffer?.set(bufOfs, innerFlag)
                                faceModeBuffer?.set(bufOfs, if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL)
                                bufOfs++
                            }
                        }
                        if (indexBuffer != null) for (index in cycle.vertices.indices) {
                            val next = (index + 1) % cycle.vertices.size
                            val surfaceId = nextSurfaceId++
                            indexBuffer.indexTriangle(base + 2 * index, base + 2 * index + 1, base + 2 * next, false, surfaceId)
                            indexBuffer.indexTriangle(base + 2 * index + 1, base + 2 * next + 1, base + 2 * next, false, surfaceId)
                        }
                        continue
                    }
                    val segments = cycle.vertices.indices.filter { index ->
                        cycle.segmentSources[index].isNotEmpty()
                    }
                    for (index in segments) {
                        val base = bufOfs
                        val a = cycle.vertices[index]
                        val b = cycle.vertices[(index + 1) % cycle.vertices.size]
                        val sideNormal = (b cross a).unit
                        for (position in listOf(a, b)) for (innerFlag in 0..1) {
                                positionBuffer[bufOfs] = position
                                lightNormalBuffer[bufOfs] = sideNormal
                                expandDirBuffer[bufOfs] = f
                                rimDirBuffer[bufOfs] = Vec3.ZERO
                                rimMaxBuffer[bufOfs] = 0.0
                                colorBuffer[bufOfs] = faceColor
                                innerBuffer?.set(bufOfs, innerFlag)
                                faceModeBuffer?.set(bufOfs, if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL)
                                bufOfs++
                        }
                        if (indexBuffer != null) {
                            val surfaceId = nextSurfaceId++
                            indexBuffer.indexTriangle(base, base + 1, base + 2, false, surfaceId)
                            indexBuffer.indexTriangle(base + 1, base + 3, base + 2, false, surfaceId)
                        }
                    }
                }
            }

            for (f in poly.fs) {
                val faceColor = printColor ?: PolyStyle.faceColor(f)
                val resolved = poly.resolvedFaces[f.id]
                // Note: In GL front faces are CCW
                if (faceShown(f)) {
                    makeFace(f, resolved, faceColor,false)
                    if (hasHiddenFaces || includeExpand) {
                        makeFace(f, resolved, faceColor, true)
                    }
                } else {
                    val rimMeshes = rimMeshesByFace[f.id]
                    if (rimMeshes != null && includeRim) {
                        makeResolvedRim(f, rimMeshes, faceColor, false)
                        makeResolvedRim(f, rimMeshes, faceColor, true)
                        if (includeWidth) makeResolvedRimBorders(f, rimMeshes, faceColor)
                    } else {
                        if (includeRim) {
                            makeRim(f, poly.faceRim(f), faceColor, false)
                            makeRim(f, poly.faceRim(f), faceColor, true)
                        }
                        if (includeWidth) {
                            makeBorder(f, poly.faceRim(f), faceColor, false)
                        }
                    }
                }
                if (includeExpand && includeWidth) {
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

    fun buildStlRequest(exportParams: FaceExportParams): CoreStlRequest {
        return CoreStlRequest(
            presentation = CoreStlPresentation(
                poly = poly,
                hiddenFaceKinds = hiddenFaces.sorted(),
                scale = exportParams.scale,
                width = exportParams.width,
                rim = exportParams.rim,
                expand = exportParams.expand,
            ),
        )
    }
}

data class FaceExportParams(
    val scale: Double,
    val width: Double,
    val rim: Double,
    val expand: Double
)

// cullMode: 0 - no, 1 - cull front, -1 - cull back
fun FaceContext.draw(view: ViewContext, lighting: LightingContext, cullMode: Int = 0) {
    if (!drawFaces) return
    val animation = animation
    val prevOrTarget = if (animation != null) prev else target
    program.use {
        assignView(view, cullMode)

        uLightColor by lighting.lightColor
        uFillColor by lighting.fillColor
        uLightPosition by lighting.lightPosition
        uKeyLightIntensity by lighting.keyLightIntensity
        uFillLightIntensity by lighting.fillLightIntensity
        uRoughness by lighting.roughness
        uFresnelF0 by lighting.fresnelF0

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

