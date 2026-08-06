package polyhedra.core

import polyhedra.core.poly.Icosahedron
import polyhedra.core.poly.Seed
import polyhedra.core.poly.Seeds
import polyhedra.core.poly.TruncatedIcosahedron
import polyhedra.core.poly.recognizedSeedOrNull
import polyhedra.core.transform.truncated
import kotlin.test.Test
import kotlin.test.assertEquals

class SeedRecognitionTest {
    @Test
    fun distinguishesEveryCatalogSeed() {
        for (seed in Seeds) {
            assertEquals(seed, seed.poly.recognizedSeedOrNull(), "Catalog recognition for $seed")
        }
    }

    @Test
    fun recognizesTruncatedIcosahedron() {
        assertEquals(
            Seed.TruncatedIcosahedron,
            Seed.Icosahedron.poly.truncated().recognizedSeedOrNull(),
        )
    }
}
