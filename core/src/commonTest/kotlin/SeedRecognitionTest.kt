import polyhedra.common.poly.Icosahedron
import polyhedra.common.poly.Seed
import polyhedra.common.poly.Seeds
import polyhedra.common.poly.TruncatedIcosahedron
import polyhedra.common.poly.recognizedSeedOrNull
import polyhedra.common.transform.truncated
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
