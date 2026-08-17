package polyhedra.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.model.api.CoreJson
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreResponse
import polyhedra.model.api.CoreState
import polyhedra.model.poly.FaceRim
import polyhedra.model.poly.FaceThicknessJoins
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.MutableFace
import polyhedra.model.poly.MutableVertex
import polyhedra.model.poly.ResolvedRimGeometry
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.resolveFaceGeometry
import polyhedra.model.poly.outwardNormal
import polyhedra.model.poly.size
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.plus
import polyhedra.model.util.times
import polyhedra.model.util.unit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolvedRimTest {
    @Test
    fun cubeRimExpandsToTheEqualThicknessInnerEdge() {
        val poly = Seed.Cube.poly
        val width = 0.1
        val configuredRim = 0.05
        val rims = poly.resolvedRims(configuredRim, width)

        for ((face, rim) in poly.fs.zip(rims)) {
            assertEquals(width, rim.width, 1e-9)
            val expectedInset = FaceRim(face).rimDir.mapIndexed { index, direction ->
                face.fvs[index] + direction * width
            }
            assertSamePointSet(expectedInset, rim.regions.single().holes.single().vertices)
        }
    }

    @Test
    fun tetrahedronRimExpandsByItsAcuteDihedralFactor() {
        val poly = Seed.Tetrahedron.poly
        val width = 0.1
        val configuredRim = 0.05
        val allFaces = poly.fs.mapTo(linkedSetOf()) { candidate -> candidate.id }
        val joins = FaceThicknessJoins(poly, allFaces, allFaces, configuredRim)
        val expected = width * joins.rimFactor(poly.es.first())
        val rims = poly.resolvedRims(configuredRim, width)

        assertTrue(expected > configuredRim)
        assertTrue(rims.all { rim -> kotlin.math.abs(rim.width - expected) <= 1e-9 })
    }

    @Test
    fun simpleConvexFaceMatchesTheExistingInsetBoundary() {
        val poly = Seed.Cube.poly
        val face = poly.fs.first()
        val width = 0.05
        val actual = face.resolvedRim(poly.resolvedFaces[face.id], width)
        val region = actual.regions.single()
        val expectedInset = FaceRim(face).rimDir.mapIndexed { index, direction ->
            face.fvs[index] + direction * width
        }

        assertEquals(1, region.holes.size)
        assertSamePointSet(face.fvs, region.outer.vertices)
        assertSamePointSet(expectedInset, region.holes.single().vertices)
        assertEquals(face.fvs.indices.toSet(), region.sourceEdges.map { it.sourceSegmentIndex }.toSet())
    }

    @Test
    fun starPrismRimRetainsEveryPentagramEdgeOccurrence() {
        val poly = requireNotNull("SP5_2".toSeedOrNull()).poly
        val face = poly.fs.first { it.kind.id == 0 }
        val rim = face.resolvedRim(poly.resolvedFaces[face.id], 0.035)

        assertTrue(rim.regions.isNotEmpty())
        assertEquals(
            face.fvs.indices.toSet(),
            rim.regions.flatMap { it.sourceEdges }.map { it.sourceSegmentIndex }.toSet(),
        )
        assertTrue(
            rim.regions.flatMap { listOf(it.outer) + it.holes }
                .sumOf { it.vertices.size } > face.fvs.size,
            "The pentagram crossing arrangement must appear in the rim boundary",
        )
    }

    @Test
    fun starPyramidSevenHalvesKeepsEveryStarEdgeSheetContinuous() {
        val poly = requireNotNull("SY7_2".toSeedOrNull()).poly
        val face = poly.fs.single { candidate -> candidate.fvs.size == 7 }
        val rim = face.resolvedRim(poly.resolvedFaces[face.id], 0.05)
        val holes = rim.regions.single().holes
        val central = holes.single { hole -> hole.vertices.size != 3 }
        val edgeLengths = central.vertices.indices.map { index ->
            (central.vertices[(index + 1) % central.vertices.size] - central.vertices[index]).norm
        }

        assertEquals(8, holes.size)
        assertEquals(7, holes.count { hole -> hole.vertices.size == 3 })
        assertEquals(7, central.vertices.size)
        assertTrue(edgeLengths.max() - edgeLengths.min() <= 1e-7)
        assertContinuousSourceEdgeSheets(face, rim, 0.05)
    }

    @Test
    fun starPyramidFiveHalvesKeepsEveryStarEdgeSheetContinuous() {
        val poly = requireNotNull("SY5_2".toSeedOrNull()).poly
        val face = poly.fs.single { candidate -> candidate.fvs.size == 5 }
        val rim = face.resolvedRim(poly.resolvedFaces[face.id], 0.05)

        assertTrue(rim.regions.isNotEmpty())
        assertContinuousSourceEdgeSheets(face, rim, 0.05)
    }

    @Test
    fun immersedMaximumWidthClampsAtCompleteFillCoverage() {
        val poly = requireNotNull("SP5_2".toSeedOrNull()).poly
        val face = poly.fs.first { it.kind.id == 0 }
        val rim = face.resolvedRim(poly.resolvedFaces[face.id], Double.MAX_VALUE)

        assertTrue(rim.maximumWidth.isFinite() && rim.maximumWidth > 0.0)
        assertEquals(rim.maximumWidth, rim.width)
        assertTrue(rim.regions.isNotEmpty())
        val resolved = poly.resolvedFaces[face.id]
        for (cell in resolved.cells) for (triangle in cell.triangles) {
            val center = (
                resolved.vertices[triangle.a].position +
                    resolved.vertices[triangle.b].position +
                    resolved.vertices[triangle.c].position
                ) * (1.0 / 3.0)
            assertTrue(rim.containsProjected(center), "Maximum rim does not cover filled cell ${cell.id}")
        }
    }

    @Test
    fun nonPlanarSimpleRimIsClippedToItsDeterministicTrianglePlanes() {
        val face = face(
            Vec3(-1.0, -0.7, 0.12),
            Vec3(1.1, -0.8, -0.08),
            Vec3(0.9, 0.9, 0.16),
            Vec3(-0.8, 0.7, -0.11),
        )
        val resolved = resolveFaceGeometry(face)
        val rim = face.resolvedRim(resolved, FaceRim(face).maxRim / 2.0)

        assertTrue(!face.isPlanar)
        assertTrue(rim.regions.size > 1)
        assertTrue(rim.regions.all { region -> region.triangulationPatch })
        val scale = face.fvs.maxOf(Vec3::norm).coerceAtLeast(1.0)
        for (region in rim.regions) {
            val points = region.outer.vertices + region.holes.flatMap { hole -> hole.vertices }
            val a = region.outer.vertices[0]
            val b = region.outer.vertices[1]
            val c = region.outer.vertices.first { point -> ((b - a) cross (point - a)).norm > 1e-12 }
            val normal = ((b - a) cross (c - a)).unit
            assertTrue(points.all { point -> kotlin.math.abs((point - a) * normal) <= scale * 1e-8 })
        }
        assertEquals(
            face.fvs.indices.toSet(),
            rim.regions.flatMap { region -> region.sourceEdges }.map { edge -> edge.sourceSegmentIndex }.toSet(),
        )
        assertTrue(rim.regions.flatMap { region -> listOf(region.outer) + region.holes }
            .flatMap { cycle -> cycle.segmentSources }.any { sources -> sources.isEmpty() })
        assertEquals(FaceRim(face).maxRim, rim.maximumWidth)
    }

    @Test
    fun truncatedStellatedOctahedronUsesSymmetricCenterFacetsAtEveryEquivalentFace() = runTest {
        val response = evaluateCore(
            CoreRequest(CoreState("O", listOf("S", "t"), "c"), rimWidth = 0.05)
        )
        assertNull(response.error)
        val foldedFaces = response.poly.fs.filterNot { face -> face.isPlanar }

        assertEquals(6, foldedFaces.size)
        for (face in foldedFaces) {
            val resolved = response.poly.resolvedFaces[face.id]
            assertEquals(face.size + 1, resolved.vertices.size)
            assertEquals(face.size, resolved.triangles.size)
            val vertexUse = IntArray(resolved.vertices.size)
            resolved.triangles.forEach { triangle ->
                vertexUse[triangle.a]++
                vertexUse[triangle.b]++
                vertexUse[triangle.c]++
            }
            assertEquals(
                List(face.size) { 2 } + face.size,
                vertexUse.sorted(),
                "Folded face ${face.id} must fan through one symmetric interior vertex",
            )
            val rim = response.resolvedRims[face.id]
            assertTrue(rim.regions.isNotEmpty())
            assertTrue(rim.regions.all { region -> region.triangulationPatch })
            assertEquals(
                face.fvs.indices.toSet(),
                rim.regions.flatMap { region -> region.sourceEdges }
                    .map { edge -> edge.sourceSegmentIndex }
                    .toSet(),
            )
        }
    }

    @Test
    fun sharpSimpleCornerUsesTheExactSafeInset() {
        val face = face(
            Vec3(-1.0, 0.0, 0.0),
            Vec3(1.0, 0.0, 0.0),
            Vec3(0.0, 0.04, 0.0),
        )
        val width = FaceRim(face).maxRim * 0.25
        val rim = face.resolvedRim(resolveFaceGeometry(face), width)
        val inner = rim.regions.single().holes.single()
        val expected = FaceRim(face).rimDir.mapIndexed { index, direction ->
            face.fvs[index] + direction * width
        }

        assertSamePointSet(expected, inner.vertices)
    }

    @Test
    fun higherWindingFaceUsesOneContinuousOneSidedSheetPerSourceEdge() {
        val face = regularStarFace(5, 2)
        val resolved = resolveFaceGeometry(face)
        val width = 0.03
        val rim = face.resolvedRim(resolved, width)
        assertContinuousSourceEdgeSheets(face, rim, width)

        val a = face.fvs[0]
        val b = face.fvs[1]
        val midpointNearTip = a + (b - a) * 0.1
        val inward = ((b - a) cross face.unit).unit
        assertTrue(rim.containsProjected(midpointNearTip + inward * (width * 0.5)))
        assertTrue(!rim.containsProjected(midpointNearTip - inward * (width * 0.5)))
    }

    @Test
    fun asymmetricAcuteStarPyramidBoundsCollapsedMitersWithoutChangingItsTopRim() {
        val poly = polyhedron {
            repeat(5) { index ->
                val angle = 2.0 * kotlin.math.PI * index / 5.0
                vertex(kotlin.math.cos(angle), kotlin.math.sin(angle), 0.0, VertexKind(0))
            }
            val apex = vertex(0.08, -0.03, 0.002, VertexKind(1))
            face(listOf(0, 2, 4, 1, 3), FaceKind(0))
            repeat(5) { index ->
                face(listOf(index, apex.id, (index + 2) % 5), FaceKind(1))
            }
        }
        val face = poly.fs.first { candidate -> candidate.kind == FaceKind(1) }
        val configuredRim = 0.05
        val faceWidth = 0.1
        val joins = FaceThicknessJoins(poly)

        assertTrue(face.fvs.any { vertex ->
            joins.vertexDirection(face, vertex, faceWidth).norm <
                joins.vertexDirection(face, vertex).norm - 1e-9
        })
        face.fvs.forEach { vertex ->
            assertPointInsideFace(face, vertex - joins.vertexDirection(face, vertex, faceWidth) * faceWidth)
        }
        val rim = poly.resolvedRims(configuredRim, faceWidth)[face.id]
        assertEquals(configuredRim, rim.width, 1e-9)
        assertTrue(rim.regions.all { region -> region.touchesSourceBoundary(face) })
    }

    @Test
    fun immersedRimCyclesAndProvenanceAreDeterministic() {
        val poly = requireNotNull("SP7_3".toSeedOrNull()).poly
        val face = poly.fs.first { it.kind.id == 0 }
        val first = face.resolvedRim(poly.resolvedFaces[face.id], 0.03)
        val second = face.resolvedRim(poly.resolvedFaces[face.id], 0.03)

        assertEquals(CoreJson.encodeToString(first), CoreJson.encodeToString(second))
        assertTrue(first.regions.flatMap { region -> region.sourceEdges }.isNotEmpty())
    }

    @Test
    fun pyramidNineteenNinthsKeepsTriangularFaceRimsInsideTheirFaces() = runTest {
        val response = evaluateCore(
            CoreRequest(
                CoreState("SY19_9", emptyList(), "c"),
                rimWidth = 0.05,
                faceWidth = 0.1,
            ),
        )

        assertNull(response.error)
        val betaFaces = response.poly.fs.filter { face -> face.kind == FaceKind(1) }
        val materialFaces = response.poly.fs.mapTo(linkedSetOf()) { face -> face.id }
        val joins = FaceThicknessJoins(
            response.poly,
            materialFaces,
            betaFaces.mapTo(linkedSetOf()) { face -> face.id },
            rimWidth = 0.05,
        )
        assertEquals(19, betaFaces.size)
        val tolerance = response.poly.circumradius * 1e-8
        for (face in betaFaces) {
            val rim = response.resolvedRims[face.id]
            assertEquals(0.05, rim.width, tolerance)
            face.fvs.forEach { vertex ->
                assertPointInsideFace(face, vertex - joins.vertexDirection(face, vertex, 0.1) * 0.1)
            }
            for (point in rim.regions.flatMap { region ->
                region.outer.vertices + region.holes.flatMap { hole -> hole.vertices }
            }) {
                for (index in face.fvs.indices) {
                    val a = face.fvs[index]
                    val b = face.fvs[(index + 1) % face.fvs.size]
                    assertTrue(
                        ((b - a) cross (point - a)) * face <= tolerance,
                        "Face ${face.id} rim point $point is outside edge $index",
                    )
                }
            }
            assertRimStaysNearSourceEdges(face, rim)
        }
    }

    @Test
    fun allFacesProduceFiniteTessellationFreePolygonRecords() {
        val rims = Seed.DisdyakisTriacontahedron.poly.resolvedRims(0.02)

        assertEquals(Seed.DisdyakisTriacontahedron.poly.fs.size, rims.size)
        assertTrue(rims.flatMap { it.regions }.all { region ->
            (listOf(region.outer) + region.holes).all { cycle ->
                cycle.vertices.all { point -> point.x.isFinite() && point.y.isFinite() && point.z.isFinite() } &&
                    cycle.segmentSources.size == cycle.vertices.size
            }
        })
    }

    @Test
    fun rimGeometryRoundTripsThroughWorkerSerialization() {
        val poly = requireNotNull("SP5_2".toSeedOrNull()).poly
        val rim = poly.fs.first().resolvedRim(poly.resolvedFaces.first(), 0.035)

        val encoded = CoreJson.encodeToString(rim)
        val decoded = CoreJson.decodeFromString<ResolvedRimGeometry>(encoded)
        assertEquals(encoded, CoreJson.encodeToString(decoded))
    }

    @Test
    fun coreResponseCarriesPresentationSpaceRimsWithoutTriangles() = runTest {
        val response = evaluateCore(
            CoreRequest(CoreState("SP5_2", emptyList(), "c"), rimWidth = 0.035)
        )

        assertEquals(response.poly.fs.size, response.resolvedRims.size)
        assertTrue(response.resolvedRims.all { rim -> rim.width == 0.035 })
        assertTrue(response.resolvedRims.take(2).all { rim -> rim.regions.isNotEmpty() })
        val encoded = CoreJson.encodeToString(response)
        assertEquals(encoded, CoreJson.encodeToString(CoreJson.decodeFromString<CoreResponse>(encoded)))
    }

    @Test
    fun coreResponseAccountsForFaceWidthWhenConstructingRims() = runTest {
        val response = evaluateCore(
            CoreRequest(
                CoreState("C", emptyList(), "c"),
                rimWidth = 0.05,
                faceWidth = 0.1,
            ),
        )

        assertNull(response.error)
        assertTrue(response.resolvedRims.all { rim -> kotlin.math.abs(rim.width - 0.1) <= 1e-9 })
    }

    @Test
    fun resolvedStarBipyramidRimsStayInsideTheirAcuteTriangularFaces() = runTest {
        val response = evaluateCore(
            CoreRequest(CoreState("SB7_2", listOf("R"), "c"), rimWidth = 0.05)
        )
        assertNull(response.error)
        assertEquals(response.poly.fs.size, response.resolvedRims.size)
        for ((face, rim) in response.poly.fs.zip(response.resolvedRims)) {
            val region = rim.regions.single()
            val hole = region.holes.single()
            assertEquals(face.fvs.size, hole.vertices.size, "Face ${face.id}")
            val tolerance = response.poly.circumradius * 1e-8
            for (point in hole.vertices) for (index in face.fvs.indices) {
                val a = face.fvs[index]
                val b = face.fvs[(index + 1) % face.fvs.size]
                assertTrue(
                    ((b - a) cross (point - a)) * face <= tolerance,
                    "Face ${face.id} rim point $point is outside edge $index",
                )
            }
        }
    }

    private fun assertSamePointSet(expected: List<Vec3>, actual: List<Vec3>) {
        assertEquals(expected.size, actual.size)
        val scale = expected.maxOf(Vec3::norm).coerceAtLeast(1.0)
        assertTrue(expected.all { point -> actual.any { candidate -> (candidate - point).norm <= scale * 1e-7 } })
    }

    private fun assertContinuousSourceEdgeSheets(
        face: polyhedra.model.poly.Face,
        rim: ResolvedRimGeometry,
        width: Double,
    ) {
        for (index in face.fvs.indices) {
            val a = face.fvs[index]
            val b = face.fvs[(index + 1) % face.fvs.size]
            val inward = ((b - a) cross face.unit).unit
            for (parameter in listOf(0.1, 0.25, 0.5, 0.75, 0.9)) {
                val point = a + (b - a) * parameter + inward * (width * 0.5)
                assertTrue(
                    rim.containsProjected(point),
                    "Source edge $index has no continuous rim at t=$parameter",
                )
            }
        }
    }

    private fun face(vararg points: Vec3): MutableFace {
        val vertices = points.mapIndexed { index, point ->
            MutableVertex(index, point, VertexKind(0))
        }
        return MutableFace(0, vertices, FaceKind(0))
    }

    private fun regularStarFace(n: Int, q: Int): MutableFace {
        val ring = List(n) { index ->
            val angle = 2.0 * kotlin.math.PI * index / n
            Vec3(kotlin.math.cos(angle), kotlin.math.sin(angle), 0.0)
        }
        return face(*List(n) { index -> ring[(index * q) % n] }.toTypedArray())
    }

    private fun ResolvedRimGeometry.containsProjected(point: Vec3): Boolean = regions.any { region ->
        region.outer.vertices.containsProjected(point) && region.holes.none { hole ->
            hole.vertices.containsProjected(point)
        }
    }

    private fun polyhedra.model.poly.ResolvedRimRegion.touchesSourceBoundary(
        face: polyhedra.model.poly.Face,
    ): Boolean {
        val tolerance = face.fvs.maxOf(Vec3::norm).coerceAtLeast(1.0) * 1e-8
        return outer.vertices.any { point ->
            face.fvs.indices.any { index ->
                val a = face.fvs[index]
                val b = face.fvs[(index + 1) % face.fvs.size]
                val edge = b - a
                val along = ((point - a) * edge) / (edge * edge)
                along in -tolerance..(1.0 + tolerance) && ((point - a) cross edge).norm <= tolerance * edge.norm
            }
        }
    }

    private fun assertPointInsideFace(face: polyhedra.model.poly.Face, point: Vec3) {
        val orientation = ((face.fvs[1] - face.fvs[0]) cross (face.fvs[2] - face.fvs[0])) * face
        val sign = if (orientation >= 0.0) 1.0 else -1.0
        for (index in face.fvs.indices) {
            val a = face.fvs[index]
            val b = face.fvs[(index + 1) % face.fvs.size]
            assertTrue(
                sign * (((b - a) cross (point - a)) * face) >= -1e-8,
                "Point $point leaves face ${face.id} through edge $index",
            )
        }
    }

    private fun assertRimStaysNearSourceEdges(
        face: polyhedra.model.poly.Face,
        rim: ResolvedRimGeometry,
    ) {
        val tolerance = face.fvs.maxOf(Vec3::norm).coerceAtLeast(1.0) * 1e-8
        val maximumDistance = rim.width * 4.0 + tolerance
        for (point in rim.regions.flatMap { region ->
            region.outer.vertices + region.holes.flatMap { hole -> hole.vertices }
        }) {
            val nearest = face.fvs.indices.minOf { index ->
                val a = face.fvs[index]
                val edge = face.fvs[(index + 1) % face.fvs.size] - a
                val parameter = (((point - a) * edge) / (edge * edge)).coerceIn(0.0, 1.0)
                (point - (a + edge * parameter)).norm
            }
            assertTrue(
                nearest <= maximumDistance,
                "Face ${face.id} rim point $point is $nearest from every source edge " +
                    "(limit $maximumDistance)",
            )
        }
    }

    private fun List<Vec3>.containsProjected(point: Vec3): Boolean {
        var inside = false
        for (index in indices) {
            val a = this[index]
            val b = this[(index + 1) % size]
            if ((a.y > point.y) != (b.y > point.y) &&
                a.x + (b.x - a.x) * (point.y - a.y) / (b.y - a.y) > point.x
            ) inside = !inside
        }
        return inside
    }
}
