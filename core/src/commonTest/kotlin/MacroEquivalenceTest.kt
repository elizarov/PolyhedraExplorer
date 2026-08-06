package polyhedra.core

import polyhedra.core.poly.SeedType
import polyhedra.core.poly.Seeds
import polyhedra.core.poly.recognizedSeedOrNull
import polyhedra.core.poly.validate
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.fev
import polyhedra.core.transform.bevelled
import polyhedra.core.transform.cantellated
import polyhedra.core.transform.rectified
import polyhedra.core.transform.truncated
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MacroEquivalenceTest {
    @Test
    fun nativeCantellationAndTwoRectificationsHaveEquivalentCombinatorics() {
        for (seed in Seeds.filter { it.type == SeedType.Platonic }) {
            assertEquivalent(
                "Cantellated $seed",
                seed.poly.cantellated(),
                seed.poly.rectified().rectified(),
            )
        }
    }

    @Test
    fun nativeBevellingAndRectificationThenTruncationHaveEquivalentCombinatorics() {
        for (seed in Seeds.filter { it.type == SeedType.Platonic }) {
            assertEquivalent(
                "Bevelled $seed",
                seed.poly.bevelled(),
                seed.poly.rectified().truncated(),
            )
        }
    }

    @Test
    fun literalExpansionsDoNotPreserveRegularCubeGeometry() {
        val cube = Seeds.single { it.tag == "C" }.poly
        assertEquals("eC", cube.cantellated().recognizedSeedOrNull()?.tag)
        assertNull(cube.rectified().rectified().recognizedSeedOrNull())

        assertEquals("bC", cube.bevelled().recognizedSeedOrNull()?.tag)
        assertNull(cube.rectified().truncated().recognizedSeedOrNull())
    }

    private fun assertEquivalent(
        description: String,
        native: Polyhedron,
        macro: Polyhedron,
    ) {
        native.validate()
        macro.validate()
        assertEquals(native.fev(), macro.fev(), "$description F/E/V")
        assertEquals(
            native.faceKindCount.values.sorted(),
            macro.faceKindCount.values.sorted(),
            "$description face orbit sizes",
        )
        assertEquals(
            native.edgeKindCount.values.sorted(),
            macro.edgeKindCount.values.sorted(),
            "$description edge orbit sizes",
        )
        assertEquals(
            native.vertexKindCount.values.sorted(),
            macro.vertexKindCount.values.sorted(),
            "$description vertex orbit sizes",
        )
    }
}
