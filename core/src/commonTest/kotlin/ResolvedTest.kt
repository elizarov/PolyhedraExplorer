package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.resolved
import polyhedra.core.util.OperationProgressContext
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.PolyhedronContract
import polyhedra.model.api.SurfaceIntersectionClass
import polyhedra.model.poly.FEV
import polyhedra.model.poly.fev
import polyhedra.model.util.Vec3
import polyhedra.model.util.rotated
import polyhedra.model.util.toRotationAroundQuat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResolvedTest {
    @Test
    fun embeddedInputIsAnIdentity() {
        assertSame(Seed.Cube.poly, Seed.Cube.poly.resolved())
    }

    @Test
    fun resolvesEveryClassicalKeplerPoinsotSurfaceToItsPhysicalBoundary() = runTest {
        val cases = listOf(
            Seed.StellatedDodecahedron to KeplerPoinsotGeometry.stellatedDodecahedron,
            Seed.GreatDodecahedron to KeplerPoinsotGeometry.greatDodecahedron,
            Seed.GreatStellatedDodecahedron to KeplerPoinsotGeometry.greatStellatedDodecahedron,
            Seed.GreatIcosahedron to KeplerPoinsotGeometry.greatIcosahedron,
        )
        for ((seed, oracle) in cases) {
            val progress = ArrayList<Int>()
            val actual = seed.poly.resolved(OperationProgressContext(progress::add))
            actual.validateProperGeometry()
            val provenance = requireNotNull(actual.resolvedTopologyProvenance)
            assertEquals(actual.vs.size, provenance.vertices.size, seed.tag)
            assertEquals(actual.es.size, provenance.edges.size, seed.tag)
            assertEquals(actual.fs.size, provenance.faces.size, seed.tag)
            assertTrue(provenance.faces.all { it.sourceFaceIds.isNotEmpty() }, seed.tag)
            assertEquals(oracle.fev(), actual.fev(), seed.tag)
            assertEquals(actual.analyzeSymmetry().orbitCounts.f, actual.faceKinds.size, "${seed.tag} face orbits")
            assertEquals(actual.analyzeSymmetry().orbitCounts.e, actual.edgeKinds.size, "${seed.tag} edge orbits")
            assertEquals(actual.analyzeSymmetry().orbitCounts.v, actual.vertexKinds.size, "${seed.tag} vertex orbits")
            assertTrue(
                actual.geometryFingerprint().matches(oracle.geometryFingerprint()),
                seed.tag,
            )
            assertTrue(progress.zipWithNext().all { (a, b) -> a <= b }, seed.tag)
            assertEquals(100, progress.last(), seed.tag)
            assertSame(actual, actual.resolved(), "${seed.tag} idempotence")
        }
    }

    @Test
    fun resolvesAParameterizedStarPrismWithoutCatalogKnowledge() {
        val source = requireNotNull("SP5_2".toSeedOrNull()).poly
        val resolved = source.resolved()

        resolved.validateProperGeometry()
        assertEquals(PolyhedronContract.EmbeddedBoundary, resolved.analyzeGeometry().strongestContract)
        assertEquals(FEV(12, 30, 20), resolved.fev())
    }

    @Test
    fun resolvesRepresentativeMembersOfEveryStarFamily() {
        for (tag in listOf("SP5_2", "SA5_2", "SY5_2", "SB5_2")) {
            val source = requireNotNull(tag.toSeedOrNull()).poly
            val resolved = source.resolved()

            resolved.validateProperGeometry()
            assertEquals(
                PolyhedronContract.EmbeddedBoundary,
                resolved.analyzeGeometry().strongestContract,
                tag,
            )
            val symmetry = resolved.analyzeSymmetry()
            assertEquals(symmetry.orbitCounts.f, resolved.faceKinds.size, "$tag face orbits")
            assertEquals(symmetry.orbitCounts.e, resolved.edgeKinds.size, "$tag edge orbits")
            assertEquals(symmetry.orbitCounts.v, resolved.vertexKinds.size, "$tag vertex orbits")
            assertSame(resolved, resolved.resolved(), "$tag idempotence")
        }
    }

    @Test
    fun resolvesStarAntiprismSevenThirdsThroughCoreApi() = runTest {
        val source = requireNotNull("SA7_3".toSeedOrNull()).poly
        assertEquals(3, source.resolvedFaces.flatMap { it.cells }.maxOf { abs(it.winding) })

        val response = evaluateCore(CoreRequest(CoreState("SA7_3", listOf("R"), "c")))

        assertNull(response.error)
        assertEquals(listOf("R"), response.validTransformTags)
        assertEquals(FEV(72, 126, 56), response.poly.fev())
        response.poly.validateProperGeometry()
        assertEquals(
            PolyhedronContract.EmbeddedBoundary,
            response.geometryAnalysis?.strongestContract,
        )
        assertSame(response.poly, response.poly.resolved(), "SA7_3 idempotence")
    }

    @Test
    fun resolvesHigherWindingMembersOfEveryStarFamily() {
        for (tag in listOf(
            "SP7_3", "SY7_3", "SB7_3",
            "SP9_4", "SA9_4", "SY9_4", "SB9_4",
            "SP11_5", "SA11_5", "SY11_5", "SB11_5",
            "SP23_10", "SA23_10", "SY23_10", "SB23_10",
            "SA47_10",
        )) assertResolvesStarFamily(tag)
    }

    @Test
    fun resolutionTopologyAndKindsAreScaleAndRotationInvariant() {
        val source = Seed.GreatIcosahedron.poly
        val baseline = source.resolved()
        val scaled = source.scaled(7.25).resolved()
        val rotation = Vec3(1.0, 2.0, -0.5).toRotationAroundQuat(PI * 0.371)
        val rotated = polyhedron {
            source.vs.forEach { vertex -> vertex(vertex.rotated(rotation), vertex.kind) }
            source.fs.forEach { face -> face(face.fvs.map { it.id }, face.kind) }
        }.resolved()

        for (candidate in listOf(scaled, rotated)) {
            assertEquals(baseline.fev(), candidate.fev())
            assertEquals(baseline.vertexKindCount.values.sorted(), candidate.vertexKindCount.values.sorted())
            assertEquals(baseline.faceKindCount.values.sorted(), candidate.faceKindCount.values.sorted())
            assertEquals(baseline.edgeKindCount.values.sorted(), candidate.edgeKindCount.values.sorted())
            assertTrue(baseline.geometryFingerprint().matches(candidate.geometryFingerprint()))
        }
    }

    @Test
    fun serializedResolvedRunsThroughCoreApi() = runTest {
        val response = evaluateCore(CoreRequest(CoreState("SD", listOf("R"), "c")))

        assertNull(response.error)
        assertEquals(listOf("R"), response.validTransformTags)
        assertEquals(PolyhedronContract.EmbeddedBoundary, response.geometryAnalysis?.strongestContract)
        val provenance = requireNotNull(response.poly.resolvedTopologyProvenance)
        assertEquals(response.poly.vs.size, provenance.vertices.size)
        assertEquals(response.poly.es.size, provenance.edges.size)
        assertEquals(response.poly.fs.size, provenance.faces.size)
    }

    @Test
    fun resolvedClassicalStarIsNotRecognizedAsItsImmersedSeed() = runTest {
        for (seed in Seeds.filter { it.type == SeedType.KeplerPoinsot }) {
            val response = evaluateCore(
                CoreRequest(
                    CoreState(seed.tag, listOf("R"), "c"),
                    detectSeed = true,
                ),
            )

            assertNull(response.error, seed.tag)
            assertNull(response.recognizedSeedTag, seed.tag)
            assertTrue(response.poly.fev() != seed.fev, seed.tag)
        }
    }

    @Test
    fun coreReportsBothImmersionClassesIndependentlyBeforeResolvedTransform() = runTest {
        val response = evaluateCore(CoreRequest(CoreState("SD", emptyList(), "c")))
        val analysis = requireNotNull(response.geometryAnalysis)

        assertEquals(PolyhedronContract.RenderableImmersion, analysis.strongestContract)
        assertTrue(analysis.intersectionCounts.getValue(SurfaceIntersectionClass.SelfCrossingFace) > 0)
        assertTrue(analysis.intersectionCounts.getValue(SurfaceIntersectionClass.IntersectingFaces) > 0)
    }

    private fun assertResolvesStarFamily(tag: String) {
        val source = requireNotNull(tag.toSeedOrNull()).poly
        val resolved = source.resolved()

        resolved.validateProperGeometry()
        assertEquals(
            PolyhedronContract.EmbeddedBoundary,
            resolved.analyzeGeometry().strongestContract,
            tag,
        )
        assertSame(resolved, resolved.resolved(), "$tag idempotence")
    }
}
