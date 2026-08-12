package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.greatened
import polyhedra.core.transform.stellated
import polyhedra.core.transform.dual
import polyhedra.core.transform.availableOrbitTransforms
import polyhedra.core.transform.KisFace
import polyhedra.model.api.CoreIssueCode
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.TransformId
import polyhedra.model.api.TransformOperation
import polyhedra.model.api.TransformSpec
import polyhedra.model.api.tag
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
        assertEquals("Stellated dodecahedron", seeds.single { seed -> seed.tag == "SD" }.name)
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
        val stellatedDodecahedron = Seed.Dodecahedron.poly.stellated()
        assertEquals("GSD", greatDodecahedron.stellated().recognizedSeedOrNull()?.tag)
        assertEquals("GSD", stellatedDodecahedron.greatened().recognizedSeedOrNull()?.tag)
    }

    @Test
    fun classicalDualityIgnoresResolvedFaceIntersectionCells() {
        val duals = mapOf(
            "SD" to "GD",
            "GD" to "SD",
            "GSD" to "GI",
            "GI" to "GSD",
        )
        for ((sourceTag, expectedTag) in duals) {
            val source = requireNotNull(sourceTag.toSeedOrNull()).poly
            val dual = source.dual()
            assertEquals(expectedTag, dual.recognizedSeedOrNull()?.tag, sourceTag)
            assertEquals(sourceTag, dual.dual().recognizedSeedOrNull()?.tag, "$sourceTag dual dual")
            dual.validate()
        }
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

    @Test
    fun everyGeneralTransformEitherProducesProperGeometryOrAControlledRejection() = runTest {
        val transformSpecs = TransformOperation.entries
            .filter { operation -> operation != TransformOperation.None && operation != TransformOperation.Drop }
            .map { operation -> TransformSpec(TransformId(operation)) }
        val controlledErrors = setOf(
            CoreIssueCode.InvalidGeometry,
            CoreIssueCode.TransformNotApplicable,
            CoreIssueCode.TooLarge,
        )

        for (seed in Seeds.filter { candidate -> candidate.type == SeedType.KeplerPoinsot }) {
            for (spec in transformSpecs) {
                val response = evaluateCore(CoreRequest(CoreState(seed.tag, listOf(spec.tag), "c")))
                val error = response.error
                if (error == null) {
                    response.poly.validateProperGeometry()
                } else {
                    assertTrue(error.code in controlledErrors, "${seed.tag} ${spec.tag}: $error")
                    if (error.code == CoreIssueCode.InvalidGeometry) {
                        assertTrue(error.detail.orEmpty().isNotBlank(), "${seed.tag} ${spec.tag}: $error")
                    }
                }
            }
        }
    }

    @Test
    fun everyAdvertisedOrbitTransformEitherProducesProperGeometryOrAControlledRejection() {
        for (seed in Seeds.filter { candidate -> candidate.type == SeedType.KeplerPoinsot }) {
            for (transform in seed.poly.availableOrbitTransforms) {
                val result = runCatching {
                    transform.transform(seed.poly).also { poly -> poly.validateProperGeometry() }
                }
                val failure = result.exceptionOrNull()
                assertTrue(
                    failure == null || failure is IllegalArgumentException,
                    "${seed.tag} ${transform.tag}: $failure",
                )
            }
        }
    }

    @Test
    fun resolvedRegularStarsDoNotAdvertiseTopologicalSelectiveKis() = runTest {
        val seed = Seed.GreatIcosahedron
        assertTrue(seed.poly.availableOrbitTransforms.none { transform -> transform is KisFace })

        val tag = KisFace(seed.poly.faceKinds.keys.first()).tag
        val response = evaluateCore(CoreRequest(CoreState(seed.tag, listOf(tag), "c")))
        assertEquals(CoreIssueCode.TransformNotApplicable, response.error?.code)
    }
}
