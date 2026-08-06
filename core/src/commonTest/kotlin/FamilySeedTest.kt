package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.Cube
import polyhedra.core.poly.FamilySeeds
import polyhedra.core.poly.Octahedron
import polyhedra.core.poly.Seed
import polyhedra.core.poly.Tetrahedron
import polyhedra.core.poly.recognizedSeedOrNull
import polyhedra.core.poly.toSeedOrNull
import polyhedra.core.poly.validate
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.FamilySeedId
import polyhedra.model.api.SeedFamily
import polyhedra.model.api.toFamilySeedIdOrNull
import polyhedra.model.poly.FEV
import polyhedra.model.poly.fev
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun generatedFamiliesHaveExpectedTopologyAndValidGeometry() {
        for (n in listOf(3, 4, 17, 100)) {
            assertFamily(SeedFamily.Prism, n, FEV(n + 2, 3 * n, 2 * n))
            assertFamily(SeedFamily.Antiprism, n, FEV(2 * n + 2, 4 * n, 2 * n))
            assertFamily(SeedFamily.Pyramid, n, FEV(n + 1, 2 * n, n + 1))
            assertFamily(SeedFamily.Bipyramid, n, FEV(2 * n, 3 * n, n + 2))
        }
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
}
