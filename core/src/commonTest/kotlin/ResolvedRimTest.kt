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
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.MutableFace
import polyhedra.model.poly.MutableVertex
import polyhedra.model.poly.ResolvedRimGeometry
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.resolveFaceGeometry
import polyhedra.model.util.Vec3
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.plus
import polyhedra.model.util.times
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolvedRimTest {
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
    fun immersedMaximumWidthClampsAtCompleteFillCoverage() {
        val poly = requireNotNull("SP5_2".toSeedOrNull()).poly
        val face = poly.fs.first { it.kind.id == 0 }
        val rim = face.resolvedRim(poly.resolvedFaces[face.id], Double.MAX_VALUE)

        assertTrue(rim.maximumWidth.isFinite() && rim.maximumWidth > 0.0)
        assertEquals(rim.maximumWidth, rim.width)
        assertTrue(rim.regions.isNotEmpty())
        assertTrue(rim.regions.all { region -> region.holes.isEmpty() })
    }

    @Test
    fun nonPlanarSimpleRimPreservesTheSourceVerticesInThreeDimensions() {
        val face = face(
            Vec3(-1.0, -0.7, 0.12),
            Vec3(1.1, -0.8, -0.08),
            Vec3(0.9, 0.9, 0.16),
            Vec3(-0.8, 0.7, -0.11),
        )
        val resolved = resolveFaceGeometry(face)
        val rim = face.resolvedRim(resolved, FaceRim(face).maxRim / 2.0)

        assertTrue(!face.isPlanar)
        assertSamePointSet(face.fvs, rim.regions.single().outer.vertices)
        assertEquals(FaceRim(face).maxRim, rim.maximumWidth)
    }

    @Test
    fun sharpSimpleCornerUsesABoundedBevelInsteadOfAnUnboundedMiter() {
        val face = face(
            Vec3(-1.0, 0.0, 0.0),
            Vec3(1.0, 0.0, 0.0),
            Vec3(0.0, 0.04, 0.0),
        )
        val width = FaceRim(face).maxRim * 0.25
        val rim = face.resolvedRim(resolveFaceGeometry(face), width)
        val inner = rim.regions.single().holes.single()

        assertTrue(inner.vertices.size > face.fvs.size, "The sharp corner must be bevelled")
        assertTrue(inner.vertices.all { point ->
            face.fvs.minOf { source -> (point - source).norm } <= width * 4.0 + 1e-9
        })
    }

    @Test
    fun immersedExteriorAndInternalSegmentsUseFullAndHalfWidth() {
        val face = regularStarFace(5, 2)
        val resolved = resolveFaceGeometry(face)
        val width = 0.03
        val rim = face.resolvedRim(resolved, width)
        val exterior = resolved.edges.filterNot { edge -> edge.internalToFill }
            .maxBy { edge -> (resolved.vertices[edge.b].position - resolved.vertices[edge.a].position).norm }
        val internal = resolved.edges.filter { edge -> edge.internalToFill }
            .maxBy { edge -> (resolved.vertices[edge.b].position - resolved.vertices[edge.a].position).norm }

        val exteriorSamples = sampleBothSides(resolved, exterior.a, exterior.b, width * 0.75)
        val exteriorBeyond = sampleBothSides(resolved, exterior.a, exterior.b, width * 1.25)
        assertEquals(1, exteriorSamples.count { point -> rim.containsProjected(point) })
        assertEquals(0, exteriorBeyond.count { point -> rim.containsProjected(point) })

        val internalSamples = sampleBothSides(resolved, internal.a, internal.b, width * 0.4)
        val internalBeyond = sampleBothSides(resolved, internal.a, internal.b, width * 0.6)
        assertEquals(2, internalSamples.count { point -> rim.containsProjected(point) })
        assertEquals(0, internalBeyond.count { point -> rim.containsProjected(point) })
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

    private fun assertSamePointSet(expected: List<Vec3>, actual: List<Vec3>) {
        assertEquals(expected.size, actual.size)
        val scale = expected.maxOf(Vec3::norm).coerceAtLeast(1.0)
        assertTrue(expected.all { point -> actual.any { candidate -> (candidate - point).norm <= scale * 1e-7 } })
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

    private fun sampleBothSides(
        resolved: polyhedra.model.poly.ResolvedFaceGeometry,
        aIndex: Int,
        bIndex: Int,
        distance: Double,
    ): List<Vec3> {
        val a = resolved.vertices[aIndex].position
        val b = resolved.vertices[bIndex].position
        val midpoint = (a + b) * 0.5
        val direction = b - a
        val normal = Vec3(-direction.y, direction.x, 0.0) * (distance / direction.norm)
        return listOf(midpoint + normal, midpoint - normal)
    }

    private fun ResolvedRimGeometry.containsProjected(point: Vec3): Boolean = regions.any { region ->
        region.outer.vertices.containsProjected(point) && region.holes.none { hole ->
            hole.vertices.containsProjected(point)
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
