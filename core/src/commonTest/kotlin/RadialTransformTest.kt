package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.RadialVertex
import polyhedra.core.transform.StellateFace
import polyhedra.core.transform.availableOrbitTransforms
import polyhedra.core.transform.canMoveRadially
import polyhedra.core.transform.kisFacesWithApexKinds
import polyhedra.core.transform.radialVertices
import polyhedra.core.transform.resolved
import polyhedra.core.transform.stellateFaces
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.TransformTweak
import polyhedra.model.api.GREAT_STELLATED_DODECAHEDRON_RADIUS
import polyhedra.model.api.PolyhedronContract
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.VertexKind
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadialTransformTest {
    @Test
    fun radialEligibilityRequiresAnIndependentTriangularVertexOrbit() {
        val tetrahedron = Seed.Tetrahedron.poly
        assertFalse(tetrahedron.canMoveRadially(VertexKind(0)), "Adjacent equal-kind vertices are excluded")

        val kis = Seed.Cube.poly.kisFacesWithApexKinds(Seed.Cube.poly.faceKinds.keys)
        val apexKind = kis.apexKinds.values.single()
        assertTrue(kis.poly.canMoveRadially(apexKind))
        assertFalse(kis.poly.canMoveRadially(kis.poly.vs.first().kind))
    }

    @Test
    fun radialIdentityAndCoordinateScalingPreserveTopology() {
        val kis = Seed.Cube.poly.kisFacesWithApexKinds(Seed.Cube.poly.faceKinds.keys)
        val apexKind = kis.apexKinds.values.single()
        val identity = kis.poly.radialVertices(apexKind, 1.0)
        val moved = kis.poly.radialVertices(apexKind, 1.4)

        assertTrue(identity === kis.poly)
        assertEquals(kis.poly.fs.map { it.fvs.map { vertex -> vertex.id } }, moved.fs.map { it.fvs.map { vertex -> vertex.id } })
        for (index in kis.poly.vs.indices) {
            val expected = if (kis.poly.vs[index].kind == apexKind) kis.poly.vs[index] * 1.4 else kis.poly.vs[index]
            assertTrue((expected - moved.vs[index]).norm <= 1e-12)
        }
    }

    @Test
    fun stellateFaceIsKisFollowedByRadialMovementOfItsApexOrbit() {
        val source = Seed.Cube.poly
        val kind = source.faceKinds.keys.first()
        val kis = source.kisFacesWithApexKinds(setOf(kind))
        val expected = kis.poly.radialVertices(kis.apexKinds.getValue(kind), 1.3)
        val actual = source.stellateFaces(kind, 1.3)

        assertEquals(expected.fs.map { it.fvs.map { vertex -> vertex.id } }, actual.fs.map { it.fvs.map { vertex -> vertex.id } })
        assertTrue(expected.vs.indices.all { index -> (expected.vs[index] - actual.vs[index]).norm <= 1e-12 })
    }

    @Test
    fun orbitAvailabilityAndWorkerTagsRoundTrip() = runTest {
        val source = Seed.Cube.poly
        val stellate = source.availableOrbitTransforms.filterIsInstance<StellateFace>()
        assertEquals(source.faceKinds.size, stellate.size)

        val response = evaluateCore(CoreRequest(CoreState("C", listOf(stellate.first().tag), "c")))
        assertNull(response.error)
        response.poly.validateRenderableImmersion()
        val radialTags = response.availableOrbitTransforms.last().filter { tag -> tag.startsWith("r[") }
        assertTrue(radialTags.isNotEmpty())
    }

    @Test
    fun dynamicRadiusRangeContainsTheGreatStellatedGoldenEndpoint() = runTest {
        val tag = StellateFace(FaceKind(0)).tag
        val response = evaluateCore(CoreRequest(CoreState("D", listOf(tag), "c")))
        val radiusRange = response.transformTweakRanges.single().single { range ->
            range.tweak == TransformTweak.Radius
        }

        assertNull(response.error)
        assertTrue(GREAT_STELLATED_DODECAHEDRON_RADIUS in radiusRange.min..radiusRange.max)
        assertEquals(
            listOf("Great stellated" to GREAT_STELLATED_DODECAHEDRON_RADIUS),
            radiusRange.snaps.map { snap -> snap.label to snap.value },
        )
    }

    @Test
    fun radialSamplesProduceEmbeddedAndImmersedGeometryOrControlledDegeneracy() {
        val source = Seed.Dodecahedron.poly
        val kind = source.faceKinds.keys.first()
        val classes = linkedSetOf<PolyhedronContract>()
        val observations = mutableListOf<String>()
        for (radius in listOf(0.05, 0.1, 0.2, 0.4, 0.6, 1.0, 1.5, 2.5, 4.0, 8.0, 12.0, 20.0)) {
            runCatching { source.stellateFaces(kind, radius) }
                .onSuccess { result ->
                    result.validateRenderableImmersion()
                    val contract = result.analyzeGeometry().strongestContract
                    classes += contract
                    observations += "$radius=$contract"
                }
                .onFailure { failure ->
                    observations += "$radius=${failure.message}"
                    assertTrue(failure is IllegalArgumentException)
                }
        }
        assertTrue(PolyhedronContract.EmbeddedBoundary in classes, observations.joinToString())
        assertTrue(source.stellateFaces(kind, GREAT_STELLATED_DODECAHEDRON_RADIUS).let { !it.isConvexGeometry })
    }

    @Test
    fun greatStellatedResolvedGeometryIdentifiesTheRequiredGoldenRadius() = runTest {
        val source = Seed.Dodecahedron.poly
        val kind = source.faceKinds.keys.first()
        val kis = source.kisFacesWithApexKinds(setOf(kind))
        val apexKind = kis.apexKinds.getValue(kind)
        val target = requireNotNull("GSD".toSeedOrNull()).poly.resolved()

        assertEquals(kis.poly.vs.size, target.vs.size)
        val actual = source.stellateFaces(kind, GREAT_STELLATED_DODECAHEDRON_RADIUS)
        val normalized = actual.vs.map { vertex -> vertex * (1.0 / actual.circumradius) }
        assertTrue(normalized.all { point ->
            target.vs.any { candidate -> (candidate - point).norm <= 2e-6 }
        })
        assertTrue(target.vs.all { point ->
            normalized.any { candidate -> (candidate - point).norm <= 2e-6 }
        })
        assertEquals("GSD", actual.recognizedSeedOrNull()?.tag)
        assertTrue(kis.poly.canMoveRadially(apexKind))

        val response = evaluateCore(
            CoreRequest(
                state = CoreState(
                    seedTag = "D",
                    transformTags = listOf(
                        StellateFace(kind, GREAT_STELLATED_DODECAHEDRON_RADIUS).tag,
                    ),
                    scaleTag = "c",
                ),
                detectSeed = true,
            ),
        )
        assertEquals("GSD", response.recognizedSeedTag)
    }
}
