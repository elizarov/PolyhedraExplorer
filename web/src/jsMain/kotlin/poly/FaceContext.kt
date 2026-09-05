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
    val drawFaces by { params.view.display.value.hasFaces() }
    val hasExpand by { params.view.expandFaces.value > 0.0 }
    val hasRim by { params.view.faceRim.value > 0.0 }
    val hasWidth by { params.view.faceWidth.value > 0.0 }
    val faceRim by { params.view.faceRim.value }
    val faceWidth by { params.view.faceWidth.value }
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
    val acrylicProgram by lazy { FaceProgram(gl, acrylic = true) }

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
        val thicknessDirBuffer = createBuffer(gl, GLType.vec3)
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
            val presentationRim = exportParams?.rim ?: faceRim
            val presentationWidth = exportParams?.width ?: faceWidth
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
            val materialFaceIds = poly.fs.filterTo(linkedSetOf()) { face ->
                faceShown(face) || includeRim
            }.mapTo(linkedSetOf(), Face::id)
            val candidateRimFaceIds = if (includeRim) {
                poly.fs.filterTo(linkedSetOf()) { face -> !faceShown(face) }.mapTo(linkedSetOf(), Face::id)
            } else {
                emptySet()
            }
            val thicknessJoins = FaceThicknessJoins(
                poly,
                materialFaceIds,
            )
            val hasImmersedFaces = poly.resolvedFaces.any(ResolvedFaceGeometry::sourceBoundarySelfIntersects)
            val immersedBottomCorners = poly.immersedBottomCorners(
                presentationWidth,
                thicknessJoins,
                resolvedRimByFace,
            )
            val immersedRimFaceIds = if (includeWidth && !includeExpand) {
                candidateRimFaceIds.mapNotNullTo(linkedSetOf()) { faceId ->
                    val face = poly.fs[faceId]
                    val rim = resolvedRimByFace[faceId]
                    if (
                        !hasImmersedFaces || !face.isPlanar ||
                        rim == null || rim.regions.isEmpty()
                    ) {
                        null
                    } else {
                        faceId
                    }
                }
            } else {
                emptySet()
            }
            val immersedBottomRoles = poly.immersedBottomRoles(
                immersedRimFaceIds,
                poly.fs.filterTo(linkedSetOf(), ::faceShown).mapTo(linkedSetOf(), Face::id),
            )
            val immersedRimGeometryByFace = immersedRimFaceIds.associateWith { faceId ->
                val face = poly.fs[faceId]
                val rim = resolvedRimByFace.getValue(faceId)
                face.immersedRimGeometry(
                    rim.width,
                    presentationWidth,
                    thicknessJoins,
                    poly.resolvedFaces,
                    immersedBottomCorners,
                    immersedBottomRoles,
                    immersedRimFaceIds,
                )
            }

            fun sourceEdgeOrNull(face: Face, a: Vec3, b: Vec3): Edge? =
                thicknessJoins.sourceEdgeOrNull(face, a, b)

            fun includeRimBorder(
                face: Face,
                mesh: TriangulatedRimRegion,
                cycle: ResolvedRimCycle,
                index: Int,
            ): Boolean {
                if (mesh.triangulationPatch && cycle.segmentSources[index].isEmpty()) return false
                val next = (index + 1) % cycle.vertices.size
                if ((cycle.vertices[next] - cycle.vertices[index]).norm <= 1e-12) return false
                return sourceEdgeOrNull(face, cycle.vertices[index], cycle.vertices[next]) == null
            }

            val fallbackRimVertices = poly.fs.associate { face ->
                val effectiveWidths = thicknessJoins.effectiveRimWidths(
                    face,
                    presentationRim,
                    presentationWidth,
                )
                face.id to runCatching { face.insetVertices(effectiveWidths) }.getOrElse {
                    val fallback = min(presentationRim, poly.faceRim(face).maxRim)
                    face.fvs.mapIndexed { index, vertex ->
                        vertex + poly.faceRim(face).rimDir[index] * fallback
                    }
                }
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
                        val immersedGeometry = immersedRimGeometryByFace[f.id]
                        bufferSize += rimVertexCount +
                            (immersedGeometry?.surfaces?.sumOf { surface -> surface.vertices.size } ?: rimVertexCount)
                        indexSize += rimIndexCount +
                            (immersedGeometry?.surfaces?.sumOf { surface -> surface.triangles.size } ?: rimIndexCount)
                        if (includeWidth) {
                            if (immersedGeometry == null) {
                                val boundarySegmentCount = rimMeshes.sumOf { mesh ->
                                    mesh.cycles.sumOf { cycle ->
                                        cycle.vertices.indices.count { index -> includeRimBorder(f, mesh, cycle, index) }
                                    }
                                }
                                bufferSize += 4 * boundarySegmentCount
                                indexSize += 6 * boundarySegmentCount
                            }
                        }
                    } else {
                        if (includeRim) {
                            bufferSize += 2 * 2 * f.size
                            indexSize += 2 * 6 * f.size
                        }
                        if (includeWidth && includeRim) {
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
            thicknessDirBuffer.ensureCapacity(bufferSize)
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
                    val position = resolved.vertices[i].position
                    positionBuffer[ofs] = position
                    lightNormalBuffer[ofs] = lNorm
                    expandDirBuffer[ofs] = f
                    val thicknessDirection = if (includeExpand) f.outwardNormal else
                        thicknessJoins.direction(f, position)
                    thicknessDirBuffer[ofs] = thicknessDirection
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

            fun makeRim(f: Face, rimVertices: List<Vec3>, faceColor: Color, inner: Boolean) {
                val n = f.size
                var ofs = bufOfs
                val lNorm = if (inner) -f else f
                val innerFlag = if (inner) 1 else 0
                for (i in 0 until n) {
                    for (rim in 0..1) {
                        val position = if (rim == 0) f[i] else rimVertices[i]
                        positionBuffer[ofs] = position
                        lightNormalBuffer[ofs] = lNorm
                        expandDirBuffer[ofs] = f
                        val thicknessDirection = if (includeExpand || rim != 0) {
                            f.outwardNormal
                        } else {
                            thicknessJoins.vertexDirection(f, f[i])
                        }
                        thicknessDirBuffer[ofs] = thicknessDirection
                        rimDirBuffer[ofs] = Vec3.ZERO
                        rimMaxBuffer[ofs] = 0.0
                        colorBuffer[ofs] = faceColor
                        innerBuffer?.set(ofs, innerFlag)
                        faceModeBuffer?.set(ofs, if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL)
                        ofs++
                    }
                }
                indexBuffer?.indexRectangles(n, inner, separateSurfaces = false)
                bufOfs = ofs
            }

            fun makeBorder(f: Face, rimVertices: List<Vec3>, faceColor: Color, noRim: Boolean) {
                val n = f.size
                var ofs = bufOfs
                for (i in 0 until n) {
                    val positions = if (noRim) f.fvs else rimVertices
                    val edge = positions[(i + 1) % n] - positions[i]
                    val lNorm = if (noRim) {
                        (edge cross -f.outwardNormal).unit
                    } else {
                        (-f.outwardNormal cross edge).unit
                    }
                    for (innerFlag in 0..1) {
                        positionBuffer[ofs] = positions[i]
                        lightNormalBuffer[ofs] = lNorm
                        expandDirBuffer[ofs] = f
                        thicknessDirBuffer[ofs] = f.outwardNormal
                        rimDirBuffer[ofs] = Vec3.ZERO
                        rimMaxBuffer[ofs] = 0.0
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
                    val patchThicknessDirection = if (mesh.normal * mesh.vertices.first() >= 0.0) {
                        mesh.normal
                    } else {
                        -mesh.normal
                    }
                    val base = bufOfs
                    val surfaceId = nextSurfaceId++
                    for (position in mesh.vertices) {
                        positionBuffer[bufOfs] = position
                        lightNormalBuffer[bufOfs] = lNorm
                        expandDirBuffer[bufOfs] = f
                        val thicknessDirection = if (includeExpand) {
                            patchThicknessDirection
                        } else if (thicknessJoins.sourceEdgeOrNull(f, position) != null) {
                            thicknessJoins.direction(f, position)
                        } else {
                            patchThicknessDirection
                        }
                        thicknessDirBuffer[bufOfs] = thicknessDirection
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
                    val preferredThicknessDirection = if (mesh.normal * mesh.vertices.first() >= 0.0) {
                        mesh.normal
                    } else {
                        -mesh.normal
                    }
                    for (index in cycle.vertices.indices) {
                        if (!includeRimBorder(f, mesh, cycle, index)) continue
                        val base = bufOfs
                        val a = cycle.vertices[index]
                        val b = cycle.vertices[(index + 1) % cycle.vertices.size]
                        val sideNormal = (-preferredThicknessDirection cross (b - a)).unit
                        for (position in listOf(a, b)) for (innerFlag in 0..1) {
                            positionBuffer[bufOfs] = position
                            lightNormalBuffer[bufOfs] = sideNormal
                            expandDirBuffer[bufOfs] = f
                            thicknessDirBuffer[bufOfs] = preferredThicknessDirection
                            rimDirBuffer[bufOfs] = Vec3.ZERO
                            rimMaxBuffer[bufOfs] = 0.0
                            colorBuffer[bufOfs] = faceColor
                            innerBuffer?.set(bufOfs, innerFlag)
                            faceModeBuffer?.set(bufOfs, if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL)
                            bufOfs++
                        }
                        val surfaceId = nextSurfaceId++
                        indexBuffer?.indexTriangle(base, base + 1, base + 2, false, surfaceId)
                        indexBuffer?.indexTriangle(base + 1, base + 3, base + 2, false, surfaceId)
                    }
                }
            }

            fun makeImmersedRimGeometry(
                f: Face,
                geometry: ImmersedRimGeometry,
                faceColor: Color,
            ) {
                for (surface in geometry.surfaces) {
                    val base = bufOfs
                    for (vertex in surface.vertices) {
                        positionBuffer[bufOfs] = vertex.position
                        lightNormalBuffer[bufOfs] = surface.normal
                        expandDirBuffer[bufOfs] = f
                        thicknessDirBuffer[bufOfs] = vertex.direction
                        rimDirBuffer[bufOfs] = Vec3.ZERO
                        rimMaxBuffer[bufOfs] = 0.0
                        colorBuffer[bufOfs] = faceColor
                        innerBuffer?.set(bufOfs, if (vertex.inner) 1 else 0)
                        faceModeBuffer?.set(bufOfs, if (f.kind == selectedFace) FACE_SELECTED else FACE_NORMAL)
                        bufOfs++
                    }
                    val surfaceId = nextSurfaceId++
                    for (triangle in surface.triangles.indices step 3) {
                        indexBuffer?.indexTriangle(
                            base + surface.triangles[triangle],
                            base + surface.triangles[triangle + 1],
                            base + surface.triangles[triangle + 2],
                            false,
                            surfaceId,
                        )
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
                        val immersedGeometry = immersedRimGeometryByFace[f.id]
                        if (immersedGeometry == null) {
                            makeResolvedRim(f, rimMeshes, faceColor, true)
                            if (includeWidth) makeResolvedRimBorders(f, rimMeshes, faceColor)
                        } else {
                            makeImmersedRimGeometry(f, immersedGeometry, faceColor)
                        }
                    } else {
                        if (includeRim) {
                            makeRim(f, fallbackRimVertices.getValue(f.id), faceColor, false)
                            makeRim(f, fallbackRimVertices.getValue(f.id), faceColor, true)
                        }
                        if (includeWidth && includeRim) {
                            makeBorder(f, fallbackRimVertices.getValue(f.id), faceColor, false)
                        }
                    }
                }
                if (includeExpand && includeWidth) {
                    makeBorder(f, f.fvs, faceColor, true)
                }
            }
            positionBuffer.bindBufferData(gl)
            lightNormalBuffer.bindBufferData(gl)
            expandDirBuffer.bindBufferData(gl)
            thicknessDirBuffer.bindBufferData(gl)
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
        fun coord(j: Int) = scale * (
            positionBuffer[i, j] -
            thicknessDirBuffer[i, j] * innerBuffer[i] * width +
            rimDirBuffer[i, j] * ri +
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

internal data class ImmersedRimVertex(
    val position: Vec3,
    val direction: Vec3,
    val inner: Boolean,
) {
    fun rendered(width: Double): Vec3 = if (inner) position - direction * width else position
}

internal data class ImmersedRimSurface(
    val vertices: List<ImmersedRimVertex>,
    val triangles: List<Int>,
    val normal: Vec3,
)

internal data class ImmersedRimGeometry(
    val undersides: List<ImmersedRimSurface>,
    val transitionWalls: List<ImmersedRimSurface>,
    val openingWalls: List<ImmersedRimSurface>,
) {
    val surfaces: List<ImmersedRimSurface> get() = undersides + transitionWalls + openingWalls
}

internal data class ImmersedBottomCorners(
    val full: Map<Int, Vec3>,
)

internal enum class ImmersedBottomRole {
    Full,
    Standard,
}

internal fun Polyhedron.immersedBottomRoles(
    immersedRimFaceIds: Set<Int>,
    shownFaceIds: Set<Int>,
): Map<Int, ImmersedBottomRole> {
    require(immersedRimFaceIds.intersect(shownFaceIds).isEmpty())
    return buildMap {
        for (faceId in immersedRimFaceIds) {
            put(
                faceId,
                if (resolvedFaces[faceId].sourceBoundarySelfIntersects) {
                    ImmersedBottomRole.Full
                } else {
                    ImmersedBottomRole.Standard
                },
            )
        }
        for (faceId in shownFaceIds) put(faceId, ImmersedBottomRole.Standard)
    }
}

/**
 * Safe bottom corners for an immersed source topology. At a reflex source vertex, extending either
 * edge's offset independently reaches outside the other edge's winding-interior sector. The full
 * outline trims that corner for the immersed sheet. Ordinary neighboring faces retain the global
 * bisector join, and their difference is closed explicitly along the shared edge.
 */
internal fun Polyhedron.immersedBottomCorners(
    faceWidth: Double,
    joins: FaceThicknessJoins,
    rimsByFace: Map<Int, ResolvedRimGeometry>,
): ImmersedBottomCorners {
    val fullCandidates = hashMapOf<Int, MutableList<Vec3>>()
    for (face in fs.filter { candidate ->
        candidate.isPlanar && resolvedFaces[candidate.id].sourceBoundarySelfIntersects
    }) {
        val rim = rimsByFace[face.id] ?: continue
        val fullWidths = joins.requiredRimWidths(face, faceWidth).map { required ->
            min(required, rim.maximumWidth)
        }
        val full = face.insetVertices(fullWidths)
        for (index in face.fvs.indices) {
            val vertex = face.fvs[index]
            val vertexId = vertex.id
            fullCandidates.getOrPut(vertexId, ::arrayListOf) +=
                full[index] - face.outwardNormal * faceWidth
        }
    }
    fun closest(candidates: Map<Int, List<Vec3>>): Map<Int, Vec3> =
        candidates.mapValues { (vertexId, points) ->
            val vertex = vs[vertexId]
            points.minBy { point -> (point - vertex).norm }
        }
    return ImmersedBottomCorners(full = closest(fullCandidates))
}

/**
 * Builds the underside as the original uninterrupted source-edge sheets instead of deforming the
 * triangulation of their planar union. Reflex source edges terminate at safe face-local corners;
 * transition sheets connect the full and rim-limited outlines. No union triangle can bridge
 * between unrelated star openings.
 */
internal fun Face.immersedRimGeometry(
    rimWidth: Double,
    faceWidth: Double,
    joins: FaceThicknessJoins,
    resolvedFaces: List<ResolvedFaceGeometry>,
    bottomCorners: ImmersedBottomCorners,
    bottomRoles: Map<Int, ImmersedBottomRole>,
    immersedRimFaceIds: Set<Int>,
): ImmersedRimGeometry {
    val topInner = insetVertices(List(size) { rimWidth })
    fun corners(face: Face): Map<Int, Vec3> = when (bottomRoles.getValue(face.id)) {
        ImmersedBottomRole.Full -> bottomCorners.full
        ImmersedBottomRole.Standard -> face.fvs.associate { vertex ->
            vertex.id to vertex - joins.vertexDirection(face, vertex) * faceWidth
        }
    }
    data class RailPoint(val parameter: Double, val point: Vec3)

    /** Actual lower rail of one source edge; a shown face can kink at resolved edge vertices. */
    fun rail(face: Face, a: Vertex, b: Vertex): List<RailPoint> {
        val faceCorners = corners(face)
        if (face.id in immersedRimFaceIds) {
            return listOf(
                RailPoint(0.0, faceCorners.getValue(a.id)),
                RailPoint(1.0, faceCorners.getValue(b.id)),
            )
        }
        val edge = b - a
        val lengthSquared = edge * edge
        val tolerance = maxOf(edge.norm, 1.0) * 1e-8
        return (resolvedFaces[face.id].vertices.asSequence().map(ResolvedFaceVertex::position) +
            sequenceOf<Vec3>(a, b))
            .mapNotNull { position ->
                val parameter = ((position - a) * edge) / lengthSquared
                if (
                    parameter < -tolerance || parameter > 1.0 + tolerance ||
                    ((position - a) cross edge).norm > tolerance * edge.norm
                ) {
                    null
                } else {
                    RailPoint(
                        parameter.coerceIn(0.0, 1.0),
                        position - joins.direction(face, position) * faceWidth,
                    )
                }
            }
            .sortedBy(RailPoint::parameter)
            .fold(arrayListOf()) { result: ArrayList<RailPoint>, point ->
                if (result.isEmpty() || point.parameter - result.last().parameter > tolerance) {
                    result += point
                }
                result
            }
    }
    fun List<RailPoint>.pointAt(parameter: Double): Vec3 {
        val next = indexOfFirst { point -> point.parameter >= parameter }
        if (next <= 0) return first().point
        if (next < 0) return last().point
        val previous = this[next - 1]
        val following = this[next]
        val fraction = (parameter - previous.parameter) /
            (following.parameter - previous.parameter)
        return previous.point + (following.point - previous.point) * fraction
    }
    val selectedCorners = corners(this)
    val bottomOuter = fvs.map { vertex ->
        selectedCorners[vertex.id]
            ?: vertex - joins.vertexDirection(this, vertex) * faceWidth
    }
    val bottomInner = insetPoints(bottomOuter, rimWidth)
    val innerNormal = -outwardNormal

    fun orientedSurfaces(
        vertices: List<ImmersedRimVertex>,
        preferredNormal: Vec3,
    ): List<ImmersedRimSurface> {
        require(vertices.size == 4)
        val candidates = listOf(
            listOf(0, 1, 2, 0, 2, 3),
            listOf(0, 1, 3, 1, 2, 3),
        )
        val rendered = vertices.map { vertex -> vertex.rendered(faceWidth) }
        val triangles = candidates.maxBy { candidate ->
            candidate.chunked(3).minOf { (a, b, c) ->
                abs(((rendered[b] - rendered[a]) cross (rendered[c] - rendered[a])) * preferredNormal)
            }
        }
        val areaTolerance = maxOf(faceWidth, rimWidth, 1.0) * 1e-12
        return triangles.chunked(3).mapNotNull { (a, b, c) ->
            var oriented = listOf(a, b, c)
            var normal = (rendered[b] - rendered[a]) cross (rendered[c] - rendered[a])
            if (normal.norm <= areaTolerance) return@mapNotNull null
            if (normal * preferredNormal < 0.0) {
                oriented = listOf(a, c, b)
                normal = -normal
            }
            ImmersedRimSurface(
                oriented.map(vertices::get),
                listOf(0, 1, 2),
                normal.unit,
            )
        }
    }

    val undersides = fvs.indices.flatMap { index ->
        val next = (index + 1) % size
        val vertices = listOf(
            ImmersedRimVertex(bottomOuter[index], Vec3.ZERO, inner = true),
            ImmersedRimVertex(bottomOuter[next], Vec3.ZERO, inner = true),
            ImmersedRimVertex(bottomInner[next], Vec3.ZERO, inner = true),
            ImmersedRimVertex(bottomInner[index], Vec3.ZERO, inner = true),
        )
        orientedSurfaces(vertices, innerNormal)
    }
    val transitionWalls = fvs.indices.flatMap { index ->
        val edge = joins.sourceEdge(this, index)
        if (edge.l.id !in bottomRoles) return@flatMap emptyList()
        // A shown neighbor cannot own presentation geometry; two hidden neighbors use face order.
        if (edge.l.id in immersedRimFaceIds && id > edge.l.id) return@flatMap emptyList()
        val next = (index + 1) % size
        val ownRail = rail(this, fvs[index], fvs[next])
        val neighborRail = rail(edge.l, fvs[index], fvs[next])
        val parameters = (ownRail + neighborRail).map(RailPoint::parameter).distinct().sorted()
        parameters.zipWithNext().flatMap { (start, end) ->
            val ownA = ownRail.pointAt(start)
            val ownB = ownRail.pointAt(end)
            val neighborA = neighborRail.pointAt(start)
            val neighborB = neighborRail.pointAt(end)
            val tolerance = maxOf((fvs[next] - fvs[index]).norm, 1.0) * 1e-8
            if ((ownA - neighborA).norm <= tolerance && (ownB - neighborB).norm <= tolerance) {
                return@flatMap emptyList()
            }
            val vertices = listOf(
                ImmersedRimVertex(ownA, Vec3.ZERO, inner = true),
                ImmersedRimVertex(ownB, Vec3.ZERO, inner = true),
                ImmersedRimVertex(neighborB, Vec3.ZERO, inner = true),
                ImmersedRimVertex(neighborA, Vec3.ZERO, inner = true),
            )
            val wallCenter = (ownA + ownB + neighborA + neighborB) / 4.0
            val sourceCenter = (fvs[index] + fvs[next]) / 2.0
            var preferred = (ownB - ownA) cross (neighborA - ownA)
            // In the edge-normal cross-section, the source edge is inside the material and the
            // lower transition is its exterior boundary. Face/edge order alone cannot choose the
            // visible side for immersed topology, so orient the sheet away from that source edge.
            if (preferred * (sourceCenter - wallCenter) > 0.0) preferred = -preferred
            orientedSurfaces(vertices, preferred)
        }
    }
    val openingWalls = fvs.indices.flatMap { index ->
        val next = (index + 1) % size
        // Keep the quad cyclic: top A, bottom A, bottom B, top B.
        val vertices = listOf(
            ImmersedRimVertex(topInner[index], outwardNormal, inner = false),
            ImmersedRimVertex(bottomInner[index], Vec3.ZERO, inner = true),
            ImmersedRimVertex(bottomInner[next], Vec3.ZERO, inner = true),
            ImmersedRimVertex(topInner[next], outwardNormal, inner = false),
        )
        val towardOpening = outwardNormal cross (fvs[next] - fvs[index])
        orientedSurfaces(vertices, towardOpening)
    }
    return ImmersedRimGeometry(undersides, transitionWalls, openingWalls)
}

/** Uniform inset of an already clipped bottom outline, retaining any out-of-plane variation. */
private fun Face.insetPoints(points: List<Vec3>, width: Double): List<Vec3> {
    require(points.size == size)
    val edgeDirections = points.indices.map { index ->
        val edge = points[(index + 1) % points.size] - points[index]
        val projected = edge - this * (edge * this)
        require(projected.norm > EPS)
        projected.unit
    }
    val inward = edgeDirections.map { edge -> edge cross this }
    return points.indices.map { index ->
        val previous = (index + points.lastIndex) % points.size
        val first = inward[previous]
        val second = inward[index]
        val cosine = first * second
        val denominator = 1.0 - cosine * cosine
        require(abs(denominator) > EPS)
        val firstWeight = width * (1.0 - cosine) / denominator
        val secondWeight = width * (1.0 - cosine) / denominator
        points[index] + first * firstWeight + second * secondWeight
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
    val program = if (view.transparencyEnabled) acrylicProgram else program
    val renderTwoSided = view.transparencyEnabled || view.cutEnabled ||
        hiddenFaces.isNotEmpty() && poly.resolvedFaces.any(ResolvedFaceGeometry::sourceBoundarySelfIntersects)
    val restoreCulling = renderTwoSided && gl.isEnabled(GL.CULL_FACE)
    if (renderTwoSided) gl.disable(GL.CULL_FACE)
    val animation = animation
    val prevOrTarget = if (animation != null) prev else target
    try {
        program.use {
            assignView(view, cullMode)

            uLightColor by lighting.lightColor
            uFillColor by lighting.fillColor
            uLightPosition by lighting.lightPosition
            uKeyLightIntensity by lighting.keyLightIntensity
            uFillLightIntensity by lighting.fillLightIntensity
            uRoughness by if (view.transparencyEnabled) lighting.acrylicRoughness else lighting.roughness
            uFresnelF0 by if (view.transparencyEnabled) dielectricF0(lighting.acrylicIor) else lighting.fresnelF0
            uTransmission by view.transparentFaces
            uIor by lighting.acrylicIor
            uOpticalThickness by view.faceWidth
            uModelScale by view.scaleFactor
            // Acrylic transmits sampled scene radiance, never the alpha of opaque plastic.
            if (view.transparencyEnabled) uColorAlpha by 1.0
            val radius = if (animation == null) poly.circumradius else
                poly.circumradius * animation.targetFraction + animation.prevPoly.circumradius * animation.prevFraction
            uInteriorRadius by (radius + view.expandFaces) * view.scaleFactor

            uTargetFraction by (animation?.targetFraction ?: 1.0)
            uPrevFraction by (animation?.prevFraction ?: 0.0)

            aPosition by target.positionBuffer
            aLightNormal by target.lightNormalBuffer
            aExpandDir by target.expandDirBuffer
            aThicknessDir by target.thicknessDirBuffer
            aRimDir by target.rimDirBuffer
            aRimMax by target.rimMaxBuffer
            aColor by target.colorBuffer
            aPrevPosition by prevOrTarget.positionBuffer
            aPrevLightNormal by prevOrTarget.lightNormalBuffer
            aPrevExpandDir by prevOrTarget.expandDirBuffer
            aPrevThicknessDir by prevOrTarget.thicknessDirBuffer
            aPrevRimDir by prevOrTarget.rimDirBuffer
            aPrevRimMax by prevOrTarget.rimMaxBuffer
            aPrevColor by prevOrTarget.colorBuffer
            aInner by innerBuffer
            aFaceMode by faceModeBuffer
        }
        gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
        gl.drawElements(GL.TRIANGLES, indexSize, GL.UNSIGNED_INT, 0)
    } finally {
        if (restoreCulling) gl.enable(GL.CULL_FACE)
    }
}

