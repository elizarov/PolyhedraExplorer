package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.FaceRequirement
import polyhedra.core.transform.TopologyRequirement
import polyhedra.core.transform.Transform
import polyhedra.core.transform.TransformOutputPolicy
import polyhedra.core.transform.dual
import polyhedra.core.transform.rectified
import polyhedra.core.transform.regularTruncationRatio
import polyhedra.core.transform.truncated
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.PolyhedronContract
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.VertexKind
import polyhedra.model.util.MutableVec3
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransformSupportTest {
    @Test
    fun foundationalOperationDomainsAreMachineReadable() {
        assertEquals(TransformOutputPolicy.RenderableImmersion, Transform.Truncated.support.outputPolicy)
        assertEquals(TransformOutputPolicy.RenderableImmersion, Transform.Rectified.support.outputPolicy)
        assertEquals(FaceRequirement.NonSingularPlanar, Transform.Dual.support.faceRequirement)
        assertEquals(TopologyRequirement.FacePlanes, Transform.Dual.support.topologyRequirement)
        assertEquals(FaceRequirement.NonSingularPlanar, Transform.Cantellated.support.faceRequirement)
        assertEquals(TransformOutputPolicy.RenderableImmersion, Transform.Stellated.support.outputPolicy)
        assertEquals(TopologyRequirement.PlanarArrangement, Transform.Resolved.support.topologyRequirement)
    }

    @Test
    fun truncateRectifyDualAndCantellateAcceptEveryClassicalImmersion() = runTest {
        for (seedTag in listOf("SD", "GD", "GSD", "GI")) {
            for (transformTag in listOf("t~d=0.5", "a", "d", "e~c=0.5")) {
                val response = evaluateCore(CoreRequest(CoreState(seedTag, listOf(transformTag), "c")))
                assertNull(response.error, "$seedTag $transformTag: ${response.error}")
                response.poly.validateRenderableImmersion()
                assertTrue(
                    response.poly.analyzeGeometry().strongestContract.ordinal >=
                        PolyhedronContract.RenderableImmersion.ordinal,
                    "$seedTag $transformTag",
                )
            }
        }
    }

    @Test
    fun dependentFoundationalMacrosRunAtSafeSettingsOnClassicalImmersions() = runTest {
        val macroTags = listOf(
            "j",
            "N~d=0.5",
            "z~d=0.5",
            "O~c=0.5",
            "m~c=0.5~d=0.5",
        )
        for (seedTag in listOf("SD", "GD", "GSD", "GI")) {
            for (transformTag in macroTags) {
                val response = evaluateCore(CoreRequest(CoreState(seedTag, listOf(transformTag), "c")))
                assertNull(response.error, "$seedTag $transformTag: ${response.error}")
                response.poly.validateRenderableImmersion()
            }
        }
    }

    @Test
    fun regularStarTruncationUsesTheActualBoundaryStepAngle() {
        val starPrism = requireNotNull("SP5_2".toSeedOrNull()).poly

        assertEquals(
            regularTruncationRatio(2.0 * PI / 5.0),
            starPrism.regularTruncationRatio(FaceKind(0)),
            1e-12,
        )
        assertEquals(
            regularTruncationRatio(PI / 5.0),
            Seed.Dodecahedron.poly.regularTruncationRatio(),
            1e-12,
        )
    }

    @Test
    fun planeBasedOperationsRejectANonPlanarAuthoritativeFaceBeforeConstruction() {
        val cube = Seed.Cube.poly
        val warped = polyhedron {
            cube.vs.forEach { source ->
                val point = if (source.id == 0) {
                    MutableVec3(source.x, source.y, source.z + 0.15)
                } else {
                    MutableVec3(source)
                }
                vertex(point, VertexKind(source.kind.id))
            }
            cube.fs.forEach { source -> face(source.fvs, source.kind) }
        }
        warped.validateRenderableImmersion()

        val dual = Transform.Dual.applicability(warped)
        assertFalse(dual.isApplicable)
        assertTrue(dual.rejectionReason.orEmpty().contains("Planar"))
    }

    @Test
    fun fullDepthTruncationIsExactlyTheRectificationQuotient() {
        for (source in listOf(Seed.Cube.poly, requireNotNull("SP5_2".toSeedOrNull()).poly)) {
            val truncated = source.truncated(1.0)
            val rectified = source.rectified()

            assertEquals(rectified.vs.size, truncated.vs.size)
            assertEquals(rectified.fs.map { face -> face.fvs.map { it.id } }, truncated.fs.map { face -> face.fvs.map { it.id } })
            assertTrue(rectified.vs.indices.all { index ->
                (rectified.vs[index] - truncated.vs[index]).norm <= 1e-12
            })
        }
    }

    @Test
    fun generalPolarDoubleDualRestoresClassicalImmersedCoordinates() {
        for (seedTag in listOf("SD", "GD", "GSD", "GI")) {
            val source = requireNotNull(seedTag.toSeedOrNull()).poly
            val restored = source.dual().dual()
            val scale = source.circumradius / restored.circumradius

            assertEquals(source.vs.size, restored.vs.size, seedTag)
            assertTrue(source.vs.indices.all { index ->
                (source.vs[index] - restored.vs[index] * scale).norm <= 1e-7
            }, seedTag)
        }
    }
}
