package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.greatened
import polyhedra.core.transform.stellated
import polyhedra.model.api.CoreIssueCode
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.poly.FEV
import polyhedra.model.poly.fev
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KeplerPoinsotTest {
    @Test
    fun physicalSeedSurfacesAreConnectedEmbeddedManifolds() {
        val expected = mapOf(
            "SD" to FEV(60, 90, 32),
            "GD" to FEV(60, 90, 32),
            "GSD" to FEV(60, 90, 32),
            "GI" to FEV(180, 270, 92),
        )
        val seeds = Seeds.filter { seed -> seed.type == SeedType.KeplerPoinsot }
        assertEquals(expected.keys, seeds.mapTo(linkedSetOf(), Seed::tag))
        for (seed in seeds) {
            assertEquals(expected.getValue(seed.tag), seed.poly.fev(), seed.name)
            seed.poly.validate()
        }
    }

    @Test
    fun conwayConstructionsReachEveryKeplerPoinsotSeed() {
        assertEquals("SD", Seed.Dodecahedron.poly.stellated().recognizedSeedOrNull()?.tag)
        assertEquals("GD", Seed.Dodecahedron.poly.greatened().recognizedSeedOrNull()?.tag)
        assertEquals("GI", Seed.Icosahedron.poly.greatened().recognizedSeedOrNull()?.tag)

        val greatDodecahedron = Seed.Dodecahedron.poly.greatened()
        val smallStellatedDodecahedron = Seed.Dodecahedron.poly.stellated()
        assertEquals("GSD", greatDodecahedron.stellated().recognizedSeedOrNull()?.tag)
        assertEquals("GSD", smallStellatedDodecahedron.greatened().recognizedSeedOrNull()?.tag)
    }

    @Test
    fun unsupportedGreateningAndStellationExplainTheirDomain() {
        val greatenError = assertFailsWith<IllegalArgumentException> { Seed.Cube.poly.greatened() }
        assertTrue(greatenError.message.orEmpty().contains("requires a regular"))
        val stellateError = assertFailsWith<IllegalArgumentException> { Seed.Icosahedron.poly.stellated() }
        assertTrue(stellateError.message.orEmpty().contains("requires a regular"))
    }

    @Test
    fun serializedPrimitiveTagsRunThroughCoreApi() = runTest {
        val greatened = evaluateCore(CoreRequest(CoreState("D", listOf("G"), "c"), detectSeed = true))
        assertEquals(null, greatened.error)
        assertEquals("GD", greatened.recognizedSeedTag)

        val unsupported = evaluateCore(CoreRequest(CoreState("C", listOf("S"), "c")))
        assertEquals(CoreIssueCode.InvalidGeometry, unsupported.error?.code)
        assertTrue(unsupported.error?.detail.orEmpty().contains("requires a regular"))
    }
}
