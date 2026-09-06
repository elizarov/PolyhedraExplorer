package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.api.convertStl
import polyhedra.core.poly.*
import polyhedra.core.transform.*
import polyhedra.model.api.*
import polyhedra.model.poly.*
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import kotlin.test.*

class FacetingTest {
    @Test
    fun facetingsWithTheSameWireframeButDifferentFacesAreDistinct() {
        val source = Seed.Icosahedron.poly.scaled(Scale.Circumradius)
        val results = source.stellationCandidates(ConstellationOperation.Facet)
        assertEquals(setOf("GI", "GD", "SD"), results.map { it.poly.recognizedSeedOrNull()?.tag }.toSet())
        assertEquals("GI", results.first().poly.recognizedSeedOrNull()?.tag)
        val greatIcosahedron = results.first().poly
        val stellatedDodecahedron = results.single { it.poly.recognizedSeedOrNull()?.tag == "SD" }.poly
        assertEquals(greatIcosahedron.edgeSignature(2e-7), stellatedDodecahedron.edgeSignature(2e-7))
        assertNotEquals(greatIcosahedron.fs.size, stellatedDodecahedron.fs.size)
    }

    @Test
    fun platonicFacetingsKeepEveryPositionAndHaveConsistentOrbits() = runTest {
        for (seed in listOf(Seed.Tetrahedron, Seed.Cube, Seed.Octahedron, Seed.Dodecahedron, Seed.Icosahedron)) {
            val source = seed.poly.scaled(Scale.Circumradius)
            val results = source.stellationCandidatesAsync(ConstellationOperation.Facet)
            println("Faceted ${seed.name}: ${results.size} results " + results.map {
                "${it.fev} ${it.poly.components.size} members ${it.poly.recognizedSeedOrNull()?.tag}"
            })
            if (seed == Seed.Cube || seed == Seed.Icosahedron || seed == Seed.Dodecahedron) {
                assertTrue(results.isNotEmpty(), seed.name)
            }
            for (candidate in results) {
                val poly = candidate.poly
                poly.validateRenderableImmersion()
                assertFalse(poly.sameGeometryAs(source, 1e-6), seed.name)
                assertTrue(poly.vs.all { v -> source.vs.any { (it - v).norm < 1e-6 } }, seed.name)
                assertTrue(source.vs.all { v -> poly.vs.any { (it - v).norm < 1e-6 } }, seed.name)
                assertEquals(poly.faceKinds.size, candidate.symmetry.orbitCounts.f)
                // EdgeKind is an adjacency label (endpoint/face kinds), not a unique geometric
                // edge orbit when several edge lengths share those same kinds.
                assertTrue(poly.edgeKinds.size <= candidate.symmetry.orbitCounts.e)
                assertEquals(poly.vertexKinds.size, candidate.symmetry.orbitCounts.v)
                assertEquals(source.analyzeSymmetry().pointGroup, candidate.symmetry.pointGroup)
            }
        }
    }

    @Test
    fun cubeFacetingIsTheCompoundOfTwoTetrahedra() = runTest {
        val result = evaluateCore(CoreRequest(CoreState("C", listOf("F"), "c")))
        assertNull(result.error, result.error?.detail)
        assertEquals("Faceted Cube", result.polyName)
        assertEquals(FEV(8, 12, 8), result.poly.fev())
        assertEquals(2, result.poly.components.size)
        assertEquals("C2T", result.poly.recognizedSeedOrNull()?.tag)
    }

    @Test
    fun noAlternativeAndInvalidNumbersAreStructuredRejections() = runTest {
        for ((seed, tag) in listOf("T" to "F", "C" to "F~l=0", "C" to "F~l=1.5", "C" to "F~l=999")) {
            val result = evaluateCore(CoreRequest(CoreState(seed, listOf(tag), "c")))
            assertEquals(0, result.errorIndex, "$seed $tag")
            assertEquals(CoreIssueCode.TransformNotApplicable, result.error?.code, "$seed $tag")
        }
    }

    @Test
    fun workerCachesResultsAndStepsAnEarlierFacetingWhilePreservingDual() = runTest {
        val source = Seed.Icosahedron.poly
        val first = source.stellationCandidatesAsync(ConstellationOperation.Facet)
        assertSame(first, source.stellationCandidatesAsync(ConstellationOperation.Facet))
        val progress = arrayListOf<CoreProgress>()
        val response = evaluateCore(CoreRequest(
            state = CoreState("I", listOf("F~l=2", "d"), "c"),
            previousState = CoreState("I", listOf("F", "d"), "c"),
            animationDuration = 0.5,
        ), progress::add)
        assertNull(response.error, response.error?.detail)
        assertEquals("Dual Faceted 2 Icosahedron", response.polyName)
        assertTrue(response.animation.isEmpty())
        val range = response.transformTweakRanges[0].single()
        assertEquals(3.0, range.max)
        assertEquals(first.map { it.fev }, range.options.map { it.fev })
        assertEquals(1, progress.last().transformIndex)
        assertEquals(100, progress.last().done)
        assertTrue(response.poly.sameGeometryAs(first[1].poly.dual().scaled(Scale.Circumradius), 1e-6))
    }

    @Test
    fun facetedCompoundSupportsFurtherTransformsAndRimStlExport() = runTest {
        for (tag in listOf("d", "t", "R", "H")) {
            val result = evaluateCore(CoreRequest(CoreState("C", listOf("F", tag), "c")))
            assertNull(result.error, "$tag: ${result.error?.detail}")
            result.poly.validateRenderableImmersion()
        }
        val poly = Seed.Cube.poly.faceted()
        for (hidden in listOf(emptyList(), poly.faceKinds.keys.sorted())) {
            val result = convertStl(CoreStlRequest(presentation = CoreStlPresentation(
                poly = poly, hiddenFaceKinds = hidden, scale = 20.0,
                width = 0.05, rim = 0.05, expand = 0.0,
            )))
            assertNull(result.error, result.error?.reason)
            assertTrue(result.triangles.isNotEmpty())
        }
    }

    @Test
    fun mixedFaceAndCompoundInputsPreserveTheirGeometricVertices() = runTest {
        for (source in listOf(Seed.Cuboctahedron.poly, Seed.Cube.poly.faceted())) {
            val normalized = source.scaled(Scale.Circumradius)
            val results = normalized.stellationCandidatesAsync(ConstellationOperation.Facet)
            assertTrue(results.isNotEmpty())
            for (result in results) {
                result.poly.validateRenderableImmersion()
                assertTrue(result.poly.vs.all { v -> normalized.vs.any { (it - v).norm < 1e-6 } })
                assertTrue(normalized.vs.all { v -> result.poly.vs.any { (it - v).norm < 1e-6 } })
            }
        }
    }

    @Test
    fun facetingReadsVerticesWithoutRequiringPlanarSourceFaces() {
        val source = polyhedron {
            for (v in Seed.Cube.poly.vs) vertex(v * if (v.id == 0) 0.9 else 1.0)
            for (f in Seed.Cube.poly.fs) face(f.fvs.map { it.id })
        }
        assertTrue(source.fs.any { !it.isPlanar })
        val results = source.buildGenericFacetingCandidates(2e-7, null)
        assertTrue(results.isNotEmpty())
        for (result in results) {
            assertTrue(result.poly.fs.all { it.isPlanar })
            result.poly.validateRenderableImmersion()
            assertTrue(result.poly.vs.all { v -> source.vs.any { (it - v).norm < 1e-6 } })
        }
    }
}
