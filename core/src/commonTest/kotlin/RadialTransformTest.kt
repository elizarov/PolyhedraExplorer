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
import polyhedra.core.transform.stellateFaceCoplanarRadii
import polyhedra.core.transform.stellateFaces
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.TransformTweak
import polyhedra.model.api.PolyhedronContract
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.Scale
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.fev
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun coplanarityLandmarksReproduceBothClassicalDodecahedronStellations() = runTest {
        val source = Seed.Dodecahedron.poly
        val kind = source.faceKinds.keys.single()
        val landmarks = source.stellateFaceCoplanarRadii(kind)
        val targetSeeds = listOf(Seed.StellatedDodecahedron, Seed.GreatStellatedDodecahedron)
        val targetRadii = targetSeeds.associateWith { seed ->
            val target = seed.poly.resolved().scaled(Scale.Circumradius)
            assertNotNull(
                landmarks.singleOrNull { radius ->
                    sameVertexGeometry(source.stellateFaces(kind, radius), target)
                },
                "No derived coplanarity landmark reproduces resolved ${seed.name}: $landmarks",
            )
        }

        val response = evaluateCore(CoreRequest(CoreState("D", listOf(StellateFace(kind).tag), "c")))
        val radiusRange = response.transformTweakRanges.single().single { range ->
            range.tweak == TransformTweak.Radius
        }
        assertNull(response.error)
        assertEquals(
            landmarks.filter { radius -> radius in radiusRange.min..radiusRange.max },
            radiusRange.landmarks,
        )
        assertTrue(targetRadii.values.all { radius -> radius in radiusRange.landmarks })

        for ((seed, radius) in targetRadii) {
            val resolvedResponse = evaluateCore(
                CoreRequest(
                    CoreState("D", listOf(StellateFace(kind, radius).tag, "R"), "c"),
                    calculateTweakRanges = false,
                ),
            )
            assertNull(resolvedResponse.error)
            val expected = seed.poly.resolved().scaled(Scale.Circumradius)
            assertEquals(expected.fev(), resolvedResponse.poly.fev(), seed.name)
            assertTrue(
                sameVertexGeometry(resolvedResponse.poly, expected),
                "Derived Radius $radius did not reproduce resolved ${seed.name}",
            )
        }
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
        assertTrue(source.stellateFaces(kind, 0.4).let { !it.isConvexGeometry })
    }
}

private fun sameVertexGeometry(actual: Polyhedron, expected: Polyhedron): Boolean {
    val normalized = actual.scaled(Scale.Circumradius)
    if (normalized.vs.size != expected.vs.size) return false
    return normalized.vs.all { point ->
        expected.vs.any { candidate -> (candidate - point).norm <= 1e-8 }
    } && expected.vs.all { point ->
        normalized.vs.any { candidate -> (candidate - point).norm <= 1e-8 }
    }
}
