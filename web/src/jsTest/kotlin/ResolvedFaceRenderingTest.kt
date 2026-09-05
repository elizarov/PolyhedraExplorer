package polyhedra.web

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.promise
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement
import polyhedra.core.poly.polyhedron
import polyhedra.core.poly.resolvedRims
import polyhedra.core.poly.toSeedOrNull
import polyhedra.core.api.evaluateCore
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.FaceThicknessJoins
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.outwardNormal
import polyhedra.model.poly.keepsConfiguredRimWidth
import polyhedra.model.poly.size
import polyhedra.model.util.Vec3
import polyhedra.web.glsl.get
import polyhedra.web.poly.FaceContext
import polyhedra.web.poly.RenderParams
import polyhedra.web.poly.FaceExportParams
import polyhedra.web.poly.ImmersedBottomRole
import polyhedra.web.poly.immersedBottomCorners
import polyhedra.web.poly.immersedBottomRoles
import polyhedra.web.poly.immersedRimGeometry
import polyhedra.web.poly.triangulate
import polyhedra.web.params.Param
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolvedFaceRenderingTest {
    private val scope = MainScope()

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun hiddenStellatedDodecahedronUsesContinuousSourceSheetsForItsUnderside() {
        val poly = requireNotNull("SD".toSeedOrNull()).poly
        val rimWidth = 0.015
        val faceWidth = 0.129
        val params = RenderParams("", null)
        params.poly.hideFaces.updateValue(setOf(FaceKind(0)))
        params.view.faceRim.updateUnsnappedValue(rimWidth, Param.TargetValue)
        params.view.faceWidth.updateUnsnappedValue(faceWidth, Param.TargetValue)
        val rims = poly.resolvedRims(rimWidth, faceWidth)
        params.poly.updateResolvedRims(rims)
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val context = FaceContext(gl, params) { poly }
        try {
            context.performUpdate(null, 0.0)

            val rimMeshes = rims.flatMap { rim ->
                val face = poly.fs[rim.sourceFaceId]
                rim.regions.map { region -> face to region.triangulate(face) }
            }
            val allFaces = poly.fs.mapTo(linkedSetOf()) { face -> face.id }
            val rimsByFace = rims.associateBy { rim -> rim.sourceFaceId }
            val joins = FaceThicknessJoins(poly, allFaces)
            val bottomCorners = poly.immersedBottomCorners(
                faceWidth,
                joins,
                rimsByFace,
            )
            val immersedFaceIds = poly.planarFaceIds()
            val bottomRoles = poly.immersedBottomRoles(immersedFaceIds, emptySet())
            val geometryByFace = poly.fs.associate { face ->
                face.id to face.immersedRimGeometry(
                    rimsByFace.getValue(face.id).width,
                    faceWidth,
                    joins,
                    poly.resolvedFaces,
                    bottomCorners,
                    bottomRoles,
                    immersedFaceIds,
                )
            }
            assertTrue(geometryByFace.values.all { geometry -> geometry.surfaces.isNotEmpty() })
            assertTrue(geometryByFace.values.flatMap { geometry -> geometry.surfaces }.all { surface ->
                surface.vertices.size == 3 && surface.triangles == listOf(0, 1, 2)
            })

            val capBufferSize = rimMeshes.sumOf { (_, mesh) -> mesh.vertices.size } +
                geometryByFace.values.flatMap { geometry -> geometry.surfaces }.sumOf { surface -> surface.vertices.size }
            val fullIndexSize = rimMeshes.sumOf { (_, mesh) -> mesh.triangles.size } +
                geometryByFace.values.flatMap { geometry -> geometry.surfaces }.sumOf { surface -> surface.triangles.size }
            assertEquals(capBufferSize, context.bufferSize)
            assertEquals(fullIndexSize, context.indexSize)

            var triangleCount = 0
            context.exportTriangles(FaceExportParams(1.0, faceWidth, rimWidth, 0.0)) { _, _, _ ->
                triangleCount++
            }
            assertEquals(context.indexSize / 3, triangleCount)
        } finally {
            context.destroy()
        }
    }

    @Test
    fun hiddenStarPrismClosesImmersedToOrdinaryUndersideTransitions() {
        val poly = requireNotNull("SP5_2".toSeedOrNull()).poly
        assertTrue(poly.resolvedFaces.any { resolved -> resolved.sourceBoundarySelfIntersects })
        assertRenderedSourceEdgeJoins(poly, rimWidth = 0.12, faceWidth = 0.112)
        assertRenderedSourceEdgeJoins(
            poly,
            rimWidth = 0.12,
            faceWidth = 0.112,
            hiddenKinds = setOf(FaceKind(0)),
        )
    }

    @Test
    fun starAntiprismOrdinaryFacesKeepBisectorDerivedBottomJoins() {
        assertRenderedSourceEdgeJoins(
            requireNotNull("SA5_2".toSeedOrNull()).poly,
            rimWidth = 0.03333333,
            faceWidth = 0.06666667,
        )
    }

    @Test
    fun convexRimsPreserveAllSharedUndersideEdges() {
        assertRenderedSourceEdgeJoins(requireNotNull("C".toSeedOrNull()).poly, 0.12, 0.112)
        assertRenderedSourceEdgeJoins(requireNotNull("T".toSeedOrNull()).poly, 0.08, 0.1)
        assertRenderedSourceEdgeJoins(requireNotNull("P3".toSeedOrNull()).poly, 0.08, 0.1)
    }

    @Test
    fun immersedSourceSheetsRemainOrientedAcrossStarFamilies() {
        val tags = listOf(
            "SP5_2", "SP7_3",
            "SY5_2", "SY7_3",
            "SA5_2", "SA7_3",
            "SD",
        )
        assertImmersedSettings(tags)
    }

    @Test
    fun fiveHalvesStarAntiprismOpeningWallsFaceItsOpeningsAtReportedDimensions() {
        assertImmersedSettings(
            listOf("SA5_2"),
            listOf(1.0 / 30.0 to 1.0 / 15.0),
        )
    }

    @Test
    fun pyramidFifteenSeventhsSourceSheetsRemainOrientedAtDifferentSizes() {
        assertImmersedSettings(
            listOf("SY15_7"),
            listOf(0.015 to 0.1, 0.1 to 0.1),
        )
    }

    @Test
    fun pyramidNineteenNinthsNarrowRimsRemainOrientedAtDifferentThicknesses() {
        assertImmersedSettings(
            listOf("SY19_9"),
            listOf(0.015 to 0.1),
        )
    }

    @Test
    fun pyramidNineteenNinthsWideRimsRemainOrientedAtDifferentThicknesses() {
        assertImmersedSettings(
            listOf("SY19_9"),
            listOf(0.08 to 0.03),
        )
    }

    private fun assertImmersedSettings(
        tags: List<String>,
        settings: List<Pair<Double, Double>> = listOf(
            0.015 to 0.03,
            0.015 to 0.1,
            0.08 to 0.03,
            0.1 to 0.1,
        ),
    ) {
        for (tag in tags) for ((rim, width) in settings) {
            assertImmersedSourceSheetsRemainOriented(tag, rim, width)
        }
    }

    private fun assertImmersedSourceSheetsRemainOriented(
        tag: String,
        rimWidth: Double,
        faceWidth: Double,
    ) {
        val poly = requireNotNull(tag.toSeedOrNull()).poly
        val rims = poly.resolvedRims(rimWidth, faceWidth)
        val rimsByFace = rims.associateBy { rim -> rim.sourceFaceId }
        val allFaces = poly.fs.mapTo(linkedSetOf()) { face -> face.id }
        val joins = FaceThicknessJoins(poly, allFaces)
        val bottomCorners = poly.immersedBottomCorners(
            faceWidth,
            joins,
            rimsByFace,
        )
        val immersedFaces = poly.fs.filter { face -> face.isPlanar }
        val immersedFaceIds = immersedFaces.mapTo(linkedSetOf()) { face -> face.id }
        val bottomRoles = poly.immersedBottomRoles(immersedFaceIds, emptySet())
        assertTrue(immersedFaces.isNotEmpty(), "$tag must exercise immersed source sheets")
        for (face in immersedFaces) {
            val geometry = face.immersedRimGeometry(
                rimsByFace.getValue(face.id).width,
                faceWidth,
                joins,
                poly.resolvedFaces,
                bottomCorners,
                bottomRoles,
                immersedFaceIds,
            )
            for ((surfaceIndex, surface) in geometry.openingWalls.withIndex()) {
                val sourceEdge = surfaceIndex / 2
                val next = (sourceEdge + 1) % face.size
                val towardOpening = face.outwardNormal cross (face.fvs[next] - face.fvs[sourceEdge])
                assertTrue(
                    surface.normal * towardOpening > 0.0,
                    "$tag face ${face.id} opening surface $surfaceIndex is culled from its opening",
                )
            }
            for ((surfaceIndex, surface) in geometry.surfaces.withIndex()) {
                val rendered = surface.vertices.map { vertex -> vertex.rendered(faceWidth) }
                val orientations = surface.triangles.chunked(3).map { (a, b, c) ->
                    ((rendered[b] - rendered[a]) cross (rendered[c] - rendered[a])) * surface.normal
                }
                assertTrue(
                    orientations.all { orientation -> abs(orientation) > 1e-12 },
                    "$tag face ${face.id} surface $surfaceIndex has a collapsed triangle",
                )
                assertTrue(
                    orientations.size == 1 || orientations.zipWithNext().all { (a, b) -> a * b > 0.0 },
                    "$tag face ${face.id} surface $surfaceIndex folds across itself",
                )
            }
        }
    }

    private fun assertRenderedSourceEdgeJoins(
        poly: Polyhedron,
        rimWidth: Double,
        faceWidth: Double,
        hiddenKinds: Set<FaceKind> = poly.faceKinds.keys,
    ) {
        val params = RenderParams("", null)
        params.poly.hideFaces.updateValue(hiddenKinds)
        params.view.faceRim.updateUnsnappedValue(rimWidth, Param.TargetValue)
        params.view.faceWidth.updateUnsnappedValue(faceWidth, Param.TargetValue)
        val rims = poly.resolvedRims(rimWidth, faceWidth)
        params.poly.updateResolvedRims(rims)
        val rimsByFace = rims.associateBy { rim -> rim.sourceFaceId }
        val meshesByFace = rims.associate { rim ->
            val face = poly.fs[rim.sourceFaceId]
            face.id to rim.regions.map { region -> region.triangulate(face) }
        }
        val allFaces = poly.fs.mapTo(linkedSetOf()) { face -> face.id }
        val joins = FaceThicknessJoins(poly, allFaces)
        val hasImmersedFaces = poly.resolvedFaces.any { resolved -> resolved.sourceBoundarySelfIntersects }
        val bottomCorners = poly.immersedBottomCorners(
            faceWidth,
            joins,
            rimsByFace,
        )
        val shownFaceIds = poly.fs.filter { face -> face.kind !in hiddenKinds }
            .mapTo(linkedSetOf()) { face -> face.id }
        val immersedFaceIds = poly.fs.filter { face ->
            hasImmersedFaces && face.isPlanar && face.kind in hiddenKinds
        }.mapTo(linkedSetOf()) { face -> face.id }
        val bottomRoles = poly.immersedBottomRoles(immersedFaceIds, shownFaceIds)
        val geometryByFace = immersedFaceIds.associateWith { faceId ->
            val face = poly.fs[faceId]
            face.immersedRimGeometry(
                rimsByFace.getValue(face.id).width,
                faceWidth,
                joins,
                poly.resolvedFaces,
                bottomCorners,
                bottomRoles,
                immersedFaceIds,
            )
        }
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val context = FaceContext(gl, params) { poly }
        try {
            context.performUpdate(null, 0.0)
            val bottomsByFaceVertex = hashMapOf<Pair<Int, Int>, List<Vec3>>()
            var offset = 0
            for (face in poly.fs) {
                val shown = face.kind !in hiddenKinds
                val meshes = meshesByFace.getValue(face.id)
                val topCount = if (shown) {
                    poly.resolvedFaces[face.id].vertices.size
                } else {
                    meshes.sumOf { mesh -> mesh.vertices.size }
                }
                val innerCount = if (shown) {
                    topCount
                } else {
                    geometryByFace[face.id]?.surfaces?.sumOf { surface -> surface.vertices.size } ?: topCount
                }
                val innerRange = (offset + topCount) until (offset + topCount + innerCount)
                val faceBottoms = innerRange.map { index ->
                    context.target.positionBuffer.vector(index) -
                        context.target.thicknessDirBuffer.vector(index) * faceWidth
                }
                for (vertex in face.fvs) {
                    bottomsByFaceVertex[face.id to vertex.id] = faceBottoms
                }
                val boundarySegments = if (shown || geometryByFace[face.id] != null) {
                    0
                } else {
                    rimBoundarySegments(poly, meshes.map { mesh -> face to mesh })
                }
                offset += topCount + innerCount + 4 * boundarySegments
            }
            assertEquals(context.bufferSize, offset)
            for (edge in poly.directedEdges) for (vertex in listOf(edge.a, edge.b)) {
                for (face in listOf(edge.r, edge.l)) {
                    val hiddenSourceSheet = face.kind in hiddenKinds && face.id in geometryByFace
                    val expectedPoint = if (hiddenSourceSheet) {
                        when (bottomRoles.getValue(face.id)) {
                            ImmersedBottomRole.Full -> bottomCorners.full.getValue(vertex.id)
                            ImmersedBottomRole.Standard ->
                                vertex - joins.vertexDirection(face, vertex) * faceWidth
                        }
                    } else {
                        vertex - joins.vertexDirection(face, vertex) * faceWidth
                    }
                    val actual = bottomsByFaceVertex.getValue(face.id to vertex.id)
                    assertTrue(
                        actual.any { point -> (point - expectedPoint).norm <= 1e-6 },
                        "Face ${face.id} omits its local bottom join at edge " +
                            "${edge.a.id}-${edge.b.id}, vertex ${vertex.id}",
                    )
                }
            }
        } finally {
            context.destroy()
        }
    }

    private fun polyhedra.web.glsl.Float32Buffer<polyhedra.web.glsl.GLType.vec3>.vector(index: Int) =
        Vec3(this[index, 0], this[index, 1], this[index, 2])

    @Test
    fun faceBuffersConsumeWorkerSuppliedPentagramCells() {
        val poly = starPrism(5, 2)
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val context = FaceContext(gl, RenderParams("", null)) { poly }
        try {
            context.performUpdate(null, 0.0)

            assertTrue(poly.resolvedFaces.take(2).all { it.sourceBoundarySelfIntersects })
            assertEquals(40, context.bufferSize)
            assertEquals(26 * 3, context.indexSize)
        } finally {
            context.destroy()
        }
    }

    @Test
    fun hiddenPentagramRimsUseWorkerPolygonsForRenderingAndExport() {
        val poly = starPrism(5, 2)
        val params = RenderParams("", null)
        params.poly.hideFaces.updateValue(setOf(FaceKind(0)))
        val rims = poly.resolvedRims(params.view.faceRim.targetValue)
        params.poly.updateResolvedRims(rims)
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val context = FaceContext(gl, params) { poly }
        try {
            context.performUpdate(null, 0.0)

            val rimMeshes = rims.filter { it.sourceFaceKind == FaceKind(0) }.flatMap { rim ->
                val face = poly.fs[rim.sourceFaceId]
                rim.regions.map { region -> face to region.triangulate(face) }
            }
            val hiddenFaceIds = poly.fs.filter { face -> face.kind == FaceKind(0) }
                .mapTo(linkedSetOf()) { face -> face.id }
            val materialFaceIds = poly.fs.mapTo(linkedSetOf()) { face -> face.id }
            val rimsByFace = rims.associateBy { rim -> rim.sourceFaceId }
            val joins = FaceThicknessJoins(poly, materialFaceIds)
            val bottomCorners = poly.immersedBottomCorners(
                params.view.faceWidth.targetValue,
                joins,
                rimsByFace,
            )
            val immersedFaceIds = hiddenFaceIds
            val shownFaceIds = poly.fs.mapTo(linkedSetOf()) { face -> face.id } - hiddenFaceIds
            val bottomRoles = poly.immersedBottomRoles(immersedFaceIds, shownFaceIds)
            val immersedSurfaces = poly.fs.filter { face -> face.kind == FaceKind(0) }.flatMap { face ->
                face.immersedRimGeometry(
                    rimsByFace.getValue(face.id).width,
                    params.view.faceWidth.targetValue,
                    joins,
                    poly.resolvedFaces,
                    bottomCorners,
                    bottomRoles,
                    immersedFaceIds,
                ).surfaces
            }
            val visibleBufferSize = 5 * 4 * 2
            val visibleIndexSize = 5 * 2 * 3 * 2
            val capBufferSize = rimMeshes.sumOf { (_, mesh) -> mesh.vertices.size } +
                immersedSurfaces.sumOf { surface -> surface.vertices.size }
            val fullCapIndexSize = rimMeshes.sumOf { (_, mesh) -> mesh.triangles.size } +
                immersedSurfaces.sumOf { surface -> surface.triangles.size }
            val boundarySegments = rimBoundarySegments(poly, rimMeshes)
            assertEquals(
                visibleBufferSize + capBufferSize,
                context.bufferSize,
            )
            assertEquals(
                visibleIndexSize + fullCapIndexSize,
                context.indexSize,
            )

            var triangleCount = 0
            context.exportTriangles(FaceExportParams(1.0, 0.1, 0.05, 0.0)) { a, b, c ->
                assertTrue(((b - a) cross (c - a)).norm > 1e-12)
                triangleCount++
            }
            assertEquals(context.indexSize / 3, triangleCount)
        } finally {
            context.destroy()
        }
    }

    private fun Polyhedron.planarFaceIds(): Set<Int> =
        fs.filter { face -> face.isPlanar }.mapTo(linkedSetOf()) { face -> face.id }

    private fun rimBoundarySegments(
        poly: Polyhedron,
        rimMeshes: List<Pair<polyhedra.model.poly.Face, polyhedra.web.poly.TriangulatedRimRegion>>,
    ): Int {
        val joins = FaceThicknessJoins(poly)
        return rimMeshes.sumOf { (face, mesh) ->
            mesh.cycles.sumOf { cycle ->
                cycle.vertices.indices.count { index ->
                    if (mesh.triangulationPatch && cycle.segmentSources[index].isEmpty()) {
                        false
                    } else {
                        val next = (index + 1) % cycle.vertices.size
                        (cycle.vertices[next] - cycle.vertices[index]).norm > 1e-12 &&
                            joins.sourceEdgeOrNull(
                                face,
                                cycle.vertices[index],
                                cycle.vertices[next],
                            ) == null
                    }
                }
            }
        }
    }

    @Test
    fun rimTriangulationCoversOuterCycleMinusHoles() {
        val poly = starPrism(5, 2)
        for (rim in poly.resolvedRims(0.035).take(2)) {
            val face = poly.fs[rim.sourceFaceId]
            for (region in rim.regions) {
                val mesh = region.triangulate(face)
                val triangleArea = mesh.triangles.chunked(3).sumOf { (a, b, c) ->
                    abs(((mesh.vertices[b] - mesh.vertices[a]) cross
                        (mesh.vertices[c] - mesh.vertices[a])) * face) / 2.0
                }
                fun cycleArea(vertices: List<polyhedra.model.util.MutableVec3>) = abs(
                    vertices.indices.sumOf { index ->
                        (vertices[index] cross vertices[(index + 1) % vertices.size]) * face
                    } / 2.0
                )
                val expectedArea = cycleArea(region.outer.vertices) -
                    region.holes.sumOf { hole -> cycleArea(hole.vertices) }
                assertTrue(abs(triangleArea - expectedArea) <= expectedArea * 1e-7 + 1e-10)
            }
        }
    }

    @Test
    fun resolvedStarBipyramidRimTriangulationCoversOnlyItsRimRegions(): Promise<Unit> = scope.promise {
        val response = evaluateCore(
            CoreRequest(CoreState("SB7_2", listOf("R"), "c"), rimWidth = 0.05),
        )
        for (rim in response.resolvedRims) {
            val face = response.poly.fs[rim.sourceFaceId]
            for (region in rim.regions) {
                val mesh = region.triangulate(face)
                val triangleArea = mesh.triangles.chunked(3).sumOf { (a, b, c) ->
                    abs(((mesh.vertices[b] - mesh.vertices[a]) cross
                        (mesh.vertices[c] - mesh.vertices[a])) * face) / 2.0
                }
                fun cycleArea(vertices: List<polyhedra.model.util.MutableVec3>) = abs(
                    vertices.indices.sumOf { index ->
                        (vertices[index] cross vertices[(index + 1) % vertices.size]) * face
                    } / 2.0
                )
                val expectedArea = cycleArea(region.outer.vertices) -
                    region.holes.sumOf { hole -> cycleArea(hole.vertices) }
                assertTrue(
                    abs(triangleArea - expectedArea) <= expectedArea * 1e-7 + 1e-10,
                    "Face ${face.id} rim triangulation area $triangleArea != $expectedArea",
                )
            }
        }
    }

    @Test
    fun foldedRimPatchesRenderAndExportWithoutInternalSeamWalls(): Promise<Unit> = scope.promise {
        val response = evaluateCore(
            CoreRequest(CoreState("O", listOf("S", "R", "t"), "c"), rimWidth = 0.05),
        )
        val poly = response.poly
        val params = RenderParams("", null)
        params.poly.updateResolvedRims(response.resolvedRims)
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val context = FaceContext(gl, params) { poly }
        try {
            context.performUpdate(null, 0.0)
            val foldedRegions = response.resolvedRims
                .filter { rim -> !poly.fs[rim.sourceFaceId].isPlanar }
                .flatMap { rim -> rim.regions }
            assertTrue(foldedRegions.isNotEmpty())
            assertTrue(foldedRegions.all { region -> region.triangulationPatch })
            assertTrue(foldedRegions.flatMap { region -> listOf(region.outer) + region.holes }
                .flatMap { cycle -> cycle.segmentSources }.any { sources -> sources.isEmpty() })

            var triangleCount = 0
            context.exportTriangles(FaceExportParams(1.0, 0.1, 0.05, 0.0)) { a, b, c ->
                assertTrue(((b - a) cross (c - a)).norm > 1e-12)
                triangleCount++
            }
            assertEquals(context.indexSize / 3, triangleCount)
        } finally {
            context.destroy()
        }
    }

    private fun starPrism(n: Int, q: Int): Polyhedron = polyhedron {
        val bottom = List(n) { index ->
            val angle = 2.0 * PI * index / n
            vertex(Vec3(cos(angle), sin(angle), -0.35))
        }
        val top = List(n) { index ->
            val angle = 2.0 * PI * index / n
            vertex(Vec3(cos(angle), sin(angle), 0.35))
        }
        face(List(n) { index -> bottom[(index * q) % n].id }, FaceKind(0))
        face(List(n) { index -> top[((n - index) * q) % n].id }, FaceKind(0))
        for (index in 0 until n) {
            val next = (index + q) % n
            face(listOf(bottom[index].id, top[index].id, top[next].id, bottom[next].id), FaceKind(1))
        }
    }
}
