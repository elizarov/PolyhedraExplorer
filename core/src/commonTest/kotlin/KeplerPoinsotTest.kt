package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.greatenedAsync
import polyhedra.core.transform.stellatedAsync
import polyhedra.core.transform.dual
import polyhedra.core.transform.availableOrbitTransforms
import polyhedra.core.transform.KisFace
import polyhedra.model.api.CoreIssueCode
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.PolyhedronContract
import polyhedra.model.api.SurfaceIntersectionClass
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
    fun classicalSeedSurfacesPreserveRegularImmersedTopology() {
        val expected = mapOf(
            "SD" to FEV(12, 30, 12),
            "GD" to FEV(12, 30, 12),
            "GSD" to FEV(12, 30, 20),
            "GI" to FEV(20, 30, 12),
        )
        val seeds = Seeds.filter { seed -> seed.type == SeedType.KeplerPoinsot }
        assertEquals(expected.keys, seeds.mapTo(linkedSetOf(), Seed::tag))
        assertEquals("Stellated dodecahedron", seeds.single { seed -> seed.tag == "SD" }.name)
        for (seed in seeds) {
            assertEquals(expected.getValue(seed.tag), seed.poly.fev(), seed.name)
            seed.poly.validateRenderableImmersion()
            val analysis = seed.poly.analyzeGeometry()
            assertEquals(PolyhedronContract.RenderableImmersion, analysis.strongestContract, seed.name)
            assertTrue(analysis.intersectionCounts.isNotEmpty(), seed.name)
            assertTrue(
                SurfaceIntersectionClass.SelfCrossingFace in analysis.intersectionCounts ||
                    SurfaceIntersectionClass.IntersectingFaces in analysis.intersectionCounts,
                seed.name,
            )
        }
    }

    @Test
    fun conwayConstructionsReachEveryKeplerPoinsotSeed() = runTest {
        assertEquals("SD", Seed.Dodecahedron.poly.stellatedAsync().recognizedSeedOrNull()?.tag)
        assertEquals("GD", Seed.Dodecahedron.poly.greatenedAsync().recognizedSeedOrNull()?.tag)
        assertEquals("GI", Seed.Icosahedron.poly.greatenedAsync().recognizedSeedOrNull()?.tag)

        val greatDodecahedron = Seed.Dodecahedron.poly.greatenedAsync()
        val stellatedDodecahedron = Seed.Dodecahedron.poly.stellatedAsync()
        assertEquals("GSD", greatDodecahedron.stellatedAsync().recognizedSeedOrNull()?.tag)
        assertEquals("GSD", stellatedDodecahedron.greatenedAsync().recognizedSeedOrNull()?.tag)
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
            dual.validateRenderableImmersion()
        }
    }

    @Test
    fun unsupportedGreateningAndStellationExplainWhenNoQualifyingExtensionExists() = runTest {
        val greatenError = assertFailsWith<IllegalArgumentException> { Seed.Cube.poly.greatenedAsync() }
        assertTrue(greatenError.message.orEmpty().contains("unavailable"))
        val stellateError = assertFailsWith<IllegalArgumentException> { Seed.Icosahedron.poly.stellatedAsync() }
        assertTrue(stellateError.message.orEmpty().contains("unavailable"))
    }

    @Test
    fun serializedPrimitiveTagsRunThroughCoreApi() = runTest {
        val greatened = evaluateCore(CoreRequest(CoreState("D", listOf("G"), "c"), detectSeed = true))
        assertEquals(null, greatened.error)
        assertEquals("GD", greatened.recognizedSeedTag)

        val unsupported = evaluateCore(CoreRequest(CoreState("C", listOf("S"), "c")))
        assertEquals(CoreIssueCode.TransformNotApplicable, unsupported.error?.code)
        assertTrue(unsupported.error?.detail.orEmpty().contains("unavailable"))
    }

    @Test
    fun everyGeneralTransformEitherProducesProperGeometryOrAControlledRejection() = runTest {
        val transformSpecs = TransformOperation.entries
            .filter { operation -> operation !in setOf(
                TransformOperation.None,
                TransformOperation.Drop,
                TransformOperation.Resolved,
                TransformOperation.Radial,
                TransformOperation.StellateFace,
            ) }
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
                    response.poly.validateRenderableImmersion()
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
    fun localSelectiveKisOnAnImmersedSolidFailsOnlyIfItsConstructedGeometryIsInvalid() = runTest {
        val seed = Seed.GreatIcosahedron
        assertTrue(seed.poly.availableOrbitTransforms.none { transform -> transform is KisFace })

        val tag = KisFace(seed.poly.faceKinds.keys.first()).tag
        val response = evaluateCore(CoreRequest(CoreState(seed.tag, listOf(tag), "c")))
        assertEquals(CoreIssueCode.InvalidGeometry, response.error?.code)
    }
}
