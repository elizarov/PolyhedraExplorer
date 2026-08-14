package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.ConstellationOperation
import polyhedra.core.transform.sameGeometryAs
import polyhedra.core.transform.stellationCandidatesAsync
import polyhedra.core.transform.resolved
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenericGreateningTest {
    @Test
    fun genericOrderingPreservesEveryClassicalGreatening() = runTest {
        assertEquals(
            listOf("GD", "SD", "GSD"),
            Seed.Dodecahedron.poly.greatenings().map { candidate ->
                candidate.poly.recognizedSeedOrNull()?.tag
            },
        )
        assertEquals("GI", Seed.Icosahedron.poly.greatenings().first().poly.recognizedSeedOrNull()?.tag)
        assertEquals(
            listOf("GSD"),
            Seed.StellatedDodecahedron.poly.greatenings().map { candidate ->
                candidate.poly.recognizedSeedOrNull()?.tag
            },
        )
    }

    @Test
    fun mixedFaceSolidHasUsefulGreateningsBeyondTheFormerRegularRingDomain() = runTest {
        val source = Seed.Cuboctahedron.poly
        val candidates = source.greatenings()

        assertEquals(3, candidates.size)
        assertTrue(candidates.all { candidate -> candidate.poly.fs.size == source.fs.size })
        assertTrue(candidates.all { candidate -> candidate.poly.analyzeSymmetry().pointGroup == source.analyzeSymmetry().pointGroup })
        candidates.forEach { candidate ->
            candidate.poly.validateRenderableImmersion()
            candidate.poly.resolved(null).validateProperGeometry()
            val orbitCounts = candidate.poly.analyzeSymmetry().orbitCounts
            assertEquals(orbitCounts.f, candidate.poly.faceKinds.size)
            assertEquals(orbitCounts.e, candidate.poly.edgeKinds.size)
            assertEquals(orbitCounts.v, candidate.poly.vertexKinds.size)
        }
    }

    @Test
    fun genericConstructionCoversSeveralMixedFaceCatalogSolids() = runTest {
        for (seed in listOf(Seed.Cuboctahedron, Seed.TruncatedCube, Seed.TruncatedOctahedron)) {
            val candidates = seed.poly.greatenings()
            assertEquals(3, candidates.size, seed.name)
            assertTrue(candidates.all { candidate -> candidate.poly.fs.size == seed.poly.fs.size }, seed.name)
            assertTrue(candidates.all { candidate ->
                runCatching { candidate.poly.validateRenderableImmersion() }.isSuccess
            }, seed.name)
        }
    }

    @Test
    fun greateningAndStellationAreRelatedButNotInterchangeable() = runTest {
        val dodecahedron = Seed.Dodecahedron.poly
        val classicalGreatenings = dodecahedron.greatenings().map { candidate -> candidate.poly }
        val classicalStellations = dodecahedron.stellations().map { candidate -> candidate.poly }
        assertTrue(classicalGreatenings.all { greatened ->
            classicalStellations.any { stellated -> greatened.sameGeometryAs(stellated, TEST_TOLERANCE) }
        })
        assertEquals("GD", classicalGreatenings.first().recognizedSeedOrNull()?.tag)
        assertEquals("SD", classicalStellations.first().recognizedSeedOrNull()?.tag)

        val cuboctahedron = Seed.Cuboctahedron.poly
        val mixedGreatenings = cuboctahedron.greatenings().map { candidate -> candidate.poly }
        val mixedStellations = cuboctahedron.stellations().map { candidate -> candidate.poly }
        assertTrue(mixedGreatenings.isNotEmpty())
        assertTrue(mixedStellations.isNotEmpty())
        assertFalse(mixedGreatenings.any { greatened ->
            mixedStellations.any { stellated -> greatened.sameGeometryAs(stellated, TEST_TOLERANCE) }
        })
    }

    @Test
    fun workerPublishesAndNamesEveryGreateningResult() = runTest {
        val response = evaluateCore(CoreRequest(CoreState("D", listOf("G~l=2"), "c")))

        assertEquals(null, response.error)
        assertEquals("Greatened 2 Dodecahedron", response.polyName)
        val range = response.transformTweakRanges.single().single()
        assertEquals(3.0, range.max)
        assertEquals(listOf(1, 2, 3), range.options.map { option -> option.value })
    }
}

private const val TEST_TOLERANCE = 2e-7

private suspend fun polyhedra.model.poly.Polyhedron.greatenings() =
    stellationCandidatesAsync(ConstellationOperation.Greaten)

private suspend fun polyhedra.model.poly.Polyhedron.stellations() =
    stellationCandidatesAsync(ConstellationOperation.Stellate)
