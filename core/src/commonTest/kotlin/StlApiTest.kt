package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.convertStl
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.resolved
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.CoreStlPresentation
import polyhedra.model.api.CoreStlErrorKind
import polyhedra.model.api.CoreStlRequest
import polyhedra.model.api.CoreStlStage
import polyhedra.model.api.CoreStlTriangle
import polyhedra.model.api.MAX_STL_CANDIDATE_PAIRS
import polyhedra.model.api.MAX_STL_INPUT_TRIANGLES
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.VertexKind
import polyhedra.model.util.MutableVec3
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.times
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.measureTime
import kotlin.time.Duration.Companion.seconds

class StlApiTest {
    @Test
    fun convertsAClosedIndexedMeshToAValidatedPositiveVolumeSolid() = runTest {
        val progress = arrayListOf<Int>()
        val response = convertStl(Seed.Cube.poly.toStlRequest(), progress::add)

        assertNull(response.error)
        assertEquals(8, response.vertices.size)
        assertEquals(12, response.triangles.size)
        assertEquals(0, progress.first())
        assertEquals(100, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
        response.toValidationPolyhedron().validateProperGeometry()
        assertTrue(response.signedVolume6() > 0.0)
    }

    @Test
    fun unionsIntersectingClosedShellsInsteadOfExportingInternalGeometry() = runTest {
        val first = Seed.Tetrahedron.poly.toStlRequest()
        val second = Seed.Tetrahedron.poly.toStlRequest { point ->
            MutableVec3(
                point.x * 0.83 + 0.38,
                point.y * 0.91 - 0.14,
                point.z * 0.87 + 0.11,
            )
        }
        val response = convertStl(first + second)

        assertNull(response.error, response.error?.reason)
        response.toValidationPolyhedron().validateProperGeometry()
        assertTrue(response.triangles.size > first.triangles.size)
        assertTrue(response.signedVolume6() > 0.0)
    }

    @Test
    fun conversionAndFinalQuantizationAreDeterministic() = runTest {
        val request = Seed.Icosahedron.poly.toStlRequest { point ->
            MutableVec3(point.x * 12.34567, point.y * 12.34567, point.z * 12.34567)
        }

        val first = convertStl(request)
        val second = convertStl(request)
        assertEquals(first.error, second.error)
        assertEquals(first.vertices.map { Triple(it.x, it.y, it.z) }, second.vertices.map { Triple(it.x, it.y, it.z) })
        assertEquals(first.triangles, second.triangles)
    }

    @Test
    fun exportsStarPrismFiveTwoWithHiddenCapsAsAPentagramRim() = runTest {
        val poly = requireNotNull("SP5_2".toSeedOrNull()).poly
        val presentation = CoreStlPresentation(
            poly = poly,
            hiddenFaceKinds = listOf(FaceKind(0)),
            scale = 20.0,
            width = 0.08,
            rim = 0.05,
            expand = 0.0,
        )
        val response = convertStl(CoreStlRequest(presentation = presentation))

        assertNull(response.error, response.error?.reason)
        response.toValidationPolyhedron().validateProperGeometry()
        assertTrue(response.triangles.size > 20)
    }

    @Test
    fun exportsEveryKeplerPoinsotSourceAsAResolvedSolid() = runTest {
        for (seed in Seeds.filter { candidate -> candidate.type == SeedType.KeplerPoinsot }) {
            val response = convertStl(
                CoreStlRequest(
                    presentation = CoreStlPresentation(
                        poly = seed.poly,
                        scale = 20.0,
                        width = 0.0,
                        rim = 0.0,
                        expand = 0.0,
                    ),
                ),
            )

            assertNull(response.error, "${seed.tag}: ${response.error?.reason}")
            response.toValidationPolyhedron().validateProperGeometry()
            assertTrue(response.signedVolume6() > 0.0, seed.tag)
        }
    }

    @Test
    fun exportsReportedHiddenFaceConfigurationAsAResolvedSolid() = runTest {
        val source = evaluateCore(CoreRequest(CoreState("I", listOf("e", "d", "t"), "c"))).poly
        val response = convertStl(
            CoreStlRequest(
                presentation = CoreStlPresentation(
                    poly = source,
                    hiddenFaceKinds = (0..3).map(::FaceKind),
                    scale = 20.0,
                    width = 0.1,
                    rim = 0.05,
                    expand = 0.0,
                ),
            ),
        )

        assertNull(response.error, response.error?.reason)
        response.toValidationPolyhedron().validateProperGeometry()
        assertTrue(response.signedVolume6() > 0.0)
    }

    @Test
    fun exportsResolvedGreatenedDeltoidalHexecontahedronPresentations() = runTest {
        val source = evaluateCore(CoreRequest(CoreState("deD", listOf("G", "R"), "c"))).poly
        assertEquals(4, source.faceKinds.size)

        for (hiddenKinds in listOf(emptyList(), listOf(FaceKind(0), FaceKind(2)))) {
            val response = convertStl(
                CoreStlRequest(
                    presentation = CoreStlPresentation(
                        poly = source,
                        hiddenFaceKinds = hiddenKinds,
                        scale = 20.0,
                        width = 0.1,
                        rim = 0.05,
                        expand = 0.0,
                    ),
                ),
            )

            assertNull(response.error, "$hiddenKinds: ${response.error?.reason}")
            response.toValidationPolyhedron().validateProperGeometry()
            assertTrue(response.signedVolume6() > 0.0)
        }
    }

    @Test
    fun exportsHigherWindingStarArrangement() = runTest {
        val source = starPrismFixture(
            n = 7,
            q = 3,
            angleOffset = 0.06036004779651245,
            halfHeight = 0.5466230093444602,
        )
        val response = convertStl(
            CoreStlRequest(
                presentation = CoreStlPresentation(
                    poly = source,
                    hiddenFaceKinds = listOf(FaceKind(0)),
                    scale = 1.0,
                    width = 0.07498040337059061,
                    rim = 0.020393189456004112,
                    expand = 0.0,
                ),
            ),
        )

        assertNull(response.error, response.error?.reason)
        response.toValidationPolyhedron().validateProperGeometry()
        assertTrue(response.signedVolume6() > 0.0)
    }

    @Test
    fun exportsStarAntiprismSevenThirdsWithEveryFaceHiddenAsRimsQuickly() = runTest {
        val source = requireNotNull("SA7_3".toSeedOrNull()).poly
        val presentation = CoreStlPresentation(
            poly = source,
            hiddenFaceKinds = source.fs.map { face -> face.kind }.distinct(),
            scale = 20.0,
            width = 0.1,
            rim = 0.05,
            expand = 0.0,
        )
        lateinit var response: polyhedra.model.api.CoreStlResponse
        val elapsed = measureTime {
            response = convertStl(CoreStlRequest(presentation = presentation))
        }

        assertNull(response.error, response.error?.reason)
        response.toValidationPolyhedron().validateProperGeometry()
        assertTrue(response.signedVolume6() > 0.0)
        assertTrue(elapsed < 1.seconds, "Rim-only SA7_3 STL export took $elapsed")
    }

    @Test
    fun exportsSmallerStarAntiprismFiveHalvesWithEveryFaceHiddenAsRims() = runTest {
        val source = requireNotNull("SA5_2".toSeedOrNull()).poly
        val response = convertStl(
            CoreStlRequest(
                presentation = CoreStlPresentation(
                    poly = source,
                    hiddenFaceKinds = source.fs.map { face -> face.kind }.distinct(),
                    scale = 20.0,
                    width = 0.1,
                    rim = 0.05,
                    expand = 0.0,
                ),
            ),
        )

        assertNull(response.error, response.error?.reason)
        response.toValidationPolyhedron().validateProperGeometry()
        assertTrue(response.signedVolume6() > 0.0)
    }

    @Test
    fun exportsResolvedStarBipyramidSevenHalvesWithEveryFaceHiddenAsRims() = runTest {
        val source = requireNotNull("SB7_2".toSeedOrNull()).poly.resolved()
        val response = convertStl(
            CoreStlRequest(
                presentation = CoreStlPresentation(
                    poly = source,
                    hiddenFaceKinds = source.fs.map { face -> face.kind }.distinct(),
                    scale = 20.0,
                    width = 0.1,
                    rim = 0.05,
                    expand = 0.0,
                ),
            ),
        )

        assertNull(response.error, response.error?.reason)
        response.toValidationPolyhedron().validateProperGeometry()
        assertTrue(response.signedVolume6() > 0.0)
    }

    @Test
    fun exportsStarPyramidSevenHalvesWithEveryFaceHiddenAsRims() = runTest {
        assertAllHiddenRimExport("SY7_2")
    }

    @Test
    fun exportsStarPyramidSevenHalvesWithOnlyItsStarFaceHidden() = runTest {
        val source = requireNotNull("SY7_2".toSeedOrNull()).poly
        val starKind = source.fs.single { face -> face.fvs.size == 7 }.kind
        assertHiddenRimExport(source, listOf(starKind))
    }

    @Test
    fun exportsTruncatedStellatedOctahedronFoldedFacesAsRims() = runTest {
        assertFoldedRimExport(listOf("S", "t"), expectedNonPlanarFaces = 6)
    }

    @Test
    fun exportsRepeatedlyTruncatedStellatedOctahedronFoldedFacesAsRims() = runTest {
        assertFoldedRimExport(listOf("S", "t", "t"), expectedNonPlanarFaces = 6)
    }

    @Test
    fun rejectsAnOpenTriangleWithoutReturningPartialGeometry() = runTest {
        val response = convertStl(
            CoreStlRequest(
                vertices = listOf(
                    MutableVec3(0.0, 0.0, 0.0),
                    MutableVec3(1.0, 0.0, 0.0),
                    MutableVec3(0.0, 1.0, 0.0),
                ),
                triangles = listOf(CoreStlTriangle(0, 1, 2)),
            ),
        )

        val error = assertNotNull(response.error)
        assertTrue(error.reason.isNotBlank())
        assertTrue(response.vertices.isEmpty())
        assertTrue(response.triangles.isEmpty())
    }

    @Test
    fun rejectsInputAboveTheBrowserTriangleLimitBeforeGeometryWork() = runTest {
        val response = convertStl(
            CoreStlRequest(
                vertices = listOf(
                    MutableVec3(0.0, 0.0, 0.0),
                    MutableVec3(1.0, 0.0, 0.0),
                    MutableVec3(0.0, 1.0, 0.0),
                ),
                triangles = List(MAX_STL_INPUT_TRIANGLES + 1) { CoreStlTriangle(0, 1, 2) },
            ),
        )

        val error = assertNotNull(response.error)
        assertEquals(CoreStlErrorKind.Limit, error.kind)
        assertEquals(CoreStlStage.Input, error.stage)
        assertEquals("input triangles", error.limitName)
        assertEquals(MAX_STL_INPUT_TRIANGLES.toLong(), error.limit)
        assertEquals((MAX_STL_INPUT_TRIANGLES + 1).toLong(), error.observed)
        assertTrue(response.vertices.isEmpty())
        assertTrue(response.triangles.isEmpty())
    }

    @Test
    fun rejectsAnExcessiveBroadPhaseBeforeArrangement() = runTest {
        val triangleCount = 2_002
        val response = convertStl(
            CoreStlRequest(
                vertices = listOf(
                    MutableVec3(0.0, 0.0, 0.0),
                    MutableVec3(1.0, 0.0, 0.0),
                    MutableVec3(0.0, 1.0, 0.0),
                ),
                triangles = List(triangleCount) { CoreStlTriangle(0, 1, 2) },
            ),
        )

        val error = assertNotNull(response.error)
        assertEquals(CoreStlErrorKind.Limit, error.kind)
        assertEquals(CoreStlStage.BroadPhase, error.stage)
        assertEquals("broad-phase candidate triangle pairs", error.limitName)
        assertEquals(MAX_STL_CANDIDATE_PAIRS.toLong(), error.limit)
        assertEquals(MAX_STL_CANDIDATE_PAIRS.toLong() + 1, error.observed)
        assertTrue(response.vertices.isEmpty())
        assertTrue(response.triangles.isEmpty())
    }
}

private suspend fun assertAllHiddenRimExport(seedTag: String) {
    val source = requireNotNull(seedTag.toSeedOrNull()).poly
    assertHiddenRimExport(source, source.fs.map { face -> face.kind }.distinct())
}

private suspend fun assertHiddenRimExport(source: Polyhedron, hiddenFaceKinds: List<FaceKind>) {
    val response = convertStl(
        CoreStlRequest(
            presentation = CoreStlPresentation(
                poly = source,
                hiddenFaceKinds = hiddenFaceKinds,
                scale = 20.0,
                width = 0.1,
                rim = 0.05,
                expand = 0.0,
            ),
        ),
    )

    assertNull(response.error, response.error?.reason)
    response.toValidationPolyhedron().validateProperGeometry()
    assertTrue(response.signedVolume6() > 0.0)
}

private suspend fun assertFoldedRimExport(transforms: List<String>, expectedNonPlanarFaces: Int) {
    val evaluation = evaluateCore(
        CoreRequest(CoreState("O", transforms, "c"), rimWidth = 0.05),
    )
    assertNull(evaluation.error, evaluation.error?.detail)
    val source = evaluation.poly
    val hidden = source.nonPlanarFaceKinds.sorted()
    assertEquals(expectedNonPlanarFaces, source.fs.count { face -> !face.isPlanar })
    assertTrue(hidden.isNotEmpty())
    val foldedRegions = evaluation.resolvedRims.filter { rim -> !source.fs[rim.sourceFaceId].isPlanar }
        .flatMap { rim -> rim.regions }
    assertTrue(foldedRegions.isNotEmpty())
    assertTrue(foldedRegions.all { region -> region.triangulationPatch })

    val response = convertStl(
        CoreStlRequest(
            presentation = CoreStlPresentation(
                poly = source,
                hiddenFaceKinds = emptyList(),
                scale = 20.0,
                width = 0.1,
                rim = 0.05,
                expand = 0.0,
            ),
        ),
    )

    assertNull(response.error, response.error?.reason)
    response.toValidationPolyhedron().validateProperGeometry()
    assertTrue(response.signedVolume6() > 0.0)
}

private fun Polyhedron.toStlRequest(
    transform: (Vec3) -> MutableVec3 = { point -> MutableVec3(point.x, point.y, point.z) },
): CoreStlRequest {
    val vertices = arrayListOf<MutableVec3>()
    val ids = linkedMapOf<Triple<Double, Double, Double>, Int>()
    fun vertex(point: Vec3): Int {
        val transformed = transform(point)
        val key = Triple(transformed.x, transformed.y, transformed.z)
        return ids.getOrPut(key) {
            vertices += transformed
            vertices.lastIndex
        }
    }
    val triangles = fs.flatMap { face ->
        val geometry = resolvedFaces[face.id]
        geometry.triangles.map { triangle ->
            CoreStlTriangle(
                vertex(geometry.vertices[triangle.a].position),
                vertex(geometry.vertices[triangle.b].position),
                vertex(geometry.vertices[triangle.c].position),
            )
        }
    }
    return CoreStlRequest(vertices, triangles)
}

private operator fun CoreStlRequest.plus(other: CoreStlRequest): CoreStlRequest {
    val offset = vertices.size
    return CoreStlRequest(
        vertices = vertices + other.vertices,
        triangles = triangles + other.triangles.map { triangle ->
            CoreStlTriangle(triangle.a + offset, triangle.b + offset, triangle.c + offset)
        },
    )
}

private fun polyhedra.model.api.CoreStlResponse.toValidationPolyhedron(): Polyhedron = polyhedron {
    vertices.forEach { point -> vertex(point, VertexKind(0)) }
    triangles.forEachIndexed { index, triangle ->
        face(listOf(triangle.a, triangle.c, triangle.b), FaceKind(index))
    }
}

private fun polyhedra.model.api.CoreStlResponse.signedVolume6(): Double = triangles.sumOf { triangle ->
    vertices[triangle.a] * (vertices[triangle.b] cross vertices[triangle.c])
}

private fun starPrismFixture(n: Int, q: Int, angleOffset: Double, halfHeight: Double): Polyhedron =
    polyhedron(mergeIndistinguishableKinds = true) {
        for (layer in 0..1) for (index in 0 until n) {
            val angle = angleOffset + 2.0 * PI * index / n
            vertex(
                cos(angle),
                sin(angle),
                if (layer == 0) -halfHeight else halfHeight,
                VertexKind(0),
            )
        }
        val order = List(n) { index -> (index * q) % n }
        face(order, FaceKind(0))
        face(order.asReversed().map { index -> n + index }, FaceKind(0))
        for (index in order.indices) {
            val a = order[index]
            val b = order[(index + 1) % order.size]
            face(listOf(a, n + a, n + b, b), FaceKind(1))
        }
    }
