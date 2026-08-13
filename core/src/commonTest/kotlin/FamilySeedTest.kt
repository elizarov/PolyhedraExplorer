package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.Cube
import polyhedra.core.poly.analyzeGeometry
import polyhedra.core.poly.FamilySeeds
import polyhedra.core.poly.StarFamilySeeds
import polyhedra.core.poly.Octahedron
import polyhedra.core.poly.Seed
import polyhedra.core.poly.Tetrahedron
import polyhedra.core.poly.recognizedSeedOrNull
import polyhedra.core.poly.toSeedOrNull
import polyhedra.core.poly.validate
import polyhedra.core.poly.validateRenderableImmersion
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.FamilySeedId
import polyhedra.model.api.PolyhedronContract
import polyhedra.model.api.SeedFamily
import polyhedra.model.api.StarFamilySeedId
import polyhedra.model.api.toFamilySeedIdOrNull
import polyhedra.model.api.toStarFamilySeedIdOrNull
import polyhedra.model.poly.FEV
import polyhedra.model.poly.fev
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FamilySeedTest {
    @Test
    fun familyTagsRoundTripOnlyWithinBounds() {
        assertEquals(FamilySeedId(SeedFamily.Prism, 3), "P3".toFamilySeedIdOrNull())
        assertEquals(FamilySeedId(SeedFamily.Bipyramid, 100), "B100".toFamilySeedIdOrNull())
        assertNull("P2".toFamilySeedIdOrNull())
        assertNull("A101".toFamilySeedIdOrNull())
        assertNull("P03".toFamilySeedIdOrNull())
        assertNull("C".toFamilySeedIdOrNull())
        assertEquals(4 * 98, FamilySeeds.size)
        assertEquals(FamilySeeds.size, FamilySeeds.map { it.tag }.toSet().size)
    }

    @Test
    fun starFamilyTagsAreCanonicalUniquePairs() {
        assertEquals(
            StarFamilySeedId(SeedFamily.Prism, 5, 2),
            "SP5_2".toStarFamilySeedIdOrNull(),
        )
        assertEquals(
            StarFamilySeedId(SeedFamily.Bipyramid, 99, 10),
            "SB99_10".toStarFamilySeedIdOrNull(),
        )
        for (invalid in listOf(
            "SP5", "SP5_", "SP_5_2", "SP05_2", "SP5_02", "SP5_3",
            "SP6_2", "SP7_7", "SP101_2", "SP23_11", "P5_2", "SC5_2",
        )) assertNull(invalid.toStarFamilySeedIdOrNull(), invalid)

        val ids = buildList {
            for (family in SeedFamily.entries) {
                for (n in 3..100) {
                    for (q in 2..10) {
                        runCatching { StarFamilySeedId(family, n, q) }.getOrNull()?.let(::add)
                    }
                }
            }
        }
        assertEquals(ids.size, ids.map { it.tag }.distinct().size)
        for (id in ids) assertEquals(id, id.tag.toStarFamilySeedIdOrNull(), id.tag)
    }

    @Test
    fun generatedFamiliesHaveExpectedTopologyAndValidGeometry() {
        for (n in listOf(3, 4, 17, 100)) {
            assertFamily(SeedFamily.Prism, n, FEV(n + 2, 3 * n, 2 * n))
            assertFamily(SeedFamily.Antiprism, n, FEV(2 * n + 2, 4 * n, 2 * n))
            assertFamily(SeedFamily.Pyramid, n, FEV(n + 1, 2 * n, n + 1))
            assertFamily(SeedFamily.Bipyramid, n, FEV(2 * n, 3 * n, n + 2))
        }
    }

    @Test
    fun generatedStarFamiliesPreserveAbstractCountsAndResolveTheirFaces() {
        val samples = listOf(5 to 2, 7 to 2, 7 to 3, 23 to 10, 99 to 10)
        for ((n, q) in samples) {
            assertStarFamily(SeedFamily.Prism, n, q, FEV(n + 2, 3 * n, 2 * n))
            assertStarFamily(SeedFamily.Antiprism, n, q, FEV(2 * n + 2, 4 * n, 2 * n))
            assertStarFamily(SeedFamily.Pyramid, n, q, FEV(n + 1, 2 * n, n + 1))
            assertStarFamily(SeedFamily.Bipyramid, n, q, FEV(2 * n, 3 * n, n + 2))
        }
    }

    @Test
    fun everyValidStarFamilyIdentifierHasOneSeed() {
        val ids = buildList {
            for (family in SeedFamily.entries) {
                for (n in 3..100) {
                    for (q in 2..10) {
                        runCatching { StarFamilySeedId(family, n, q) }.getOrNull()?.let(::add)
                    }
                }
            }
        }
        assertEquals(ids.size, StarFamilySeeds.size)
        assertEquals(ids.map { it.tag }.toSet(), StarFamilySeeds.map { it.tag }.toSet())
        for (id in ids) assertEquals(id.tag, requireNotNull(id.tag.toSeedOrNull()).tag)
    }

    @Test
    fun overlappingFamilyMembersRecognizeFixedCatalogSeeds() {
        assertRecognized(SeedFamily.Prism, 4, Seed.Cube)
        assertRecognized(SeedFamily.Antiprism, 3, Seed.Octahedron)
        assertRecognized(SeedFamily.Pyramid, 3, Seed.Tetrahedron)
        assertRecognized(SeedFamily.Bipyramid, 4, Seed.Octahedron)
    }

    @Test
    fun coreOffersRecognitionForAnUntransformedFamilySeed() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("P4", emptyList(), "c"),
                detectSeed = true,
            ),
        )

        assertEquals("Prism 4", response.polyName)
        assertEquals("C", response.recognizedSeedTag)
        assertEquals(emptyList(), response.validTransformTags)
    }

    @Test
    fun coreLoadsAStarFamilyAsAnImmersedSourceSurface() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("SA7_3", emptyList(), "c"),
                detectSeed = true,
            ),
        )

        assertNull(response.error)
        assertEquals("Antiprism 7/3", response.polyName)
        assertEquals(FEV(16, 28, 14), response.poly.fev())
        val analysis = requireNotNull(response.geometryAnalysis)
        assertEquals(PolyhedronContract.RenderableImmersion, analysis.strongestContract)
        assertTrue(analysis.hasIntersections)
    }

    private fun assertFamily(family: SeedFamily, n: Int, expected: FEV) {
        val seed = requireNotNull(FamilySeedId(family, n).tag.toSeedOrNull())
        assertEquals(expected, seed.fev)
        assertEquals(expected, seed.poly.fev())
        seed.poly.validate()
    }

    private fun assertRecognized(family: SeedFamily, n: Int, expected: Seed) {
        val actual = requireNotNull(FamilySeedId(family, n).tag.toSeedOrNull())
        assertEquals(expected, actual.poly.recognizedSeedOrNull())
    }

    private fun assertStarFamily(family: SeedFamily, n: Int, q: Int, expected: FEV) {
        val seed = requireNotNull(StarFamilySeedId(family, n, q).tag.toSeedOrNull())
        assertEquals(expected, seed.fev)
        assertEquals(expected, seed.poly.fev())
        seed.poly.validateRenderableImmersion()
        val analysis = seed.poly.analyzeGeometry()
        assertEquals(PolyhedronContract.RenderableImmersion, analysis.strongestContract)
        assertTrue(analysis.hasIntersections, seed.name)
    }
}
