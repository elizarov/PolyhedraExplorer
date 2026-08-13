package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.ConstellationOperation
import polyhedra.core.transform.stellationCandidatesAsync
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import polyhedra.model.api.CoreIssueCode
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.TransformTweak
import polyhedra.model.poly.fev
import kotlin.test.Test
import kotlin.test.assertEquals

class FacePlaneConstellationTest {
    @Test
    fun regularDodecahedronConstellationContainsBothFirstClassicalExtensions() = runTest {
        val source = Seed.Dodecahedron.poly
        val stellated = source.stellationCandidatesAsync(ConstellationOperation.Stellate)
        val greatened = source.stellationCandidatesAsync(ConstellationOperation.Greaten)
        assertEquals(
            "SD",
            stellated.firstOrNull()?.poly?.recognizedSeedOrNull()?.tag,
            "stellated=${stellated.map { it.fev to it.poly.recognizedSeedOrNull()?.tag }}; " +
                "greatened=${greatened.map { it.fev to it.poly.recognizedSeedOrNull()?.tag }}; " +
                "distances=${listOf("SD", "GD").associateWith { tag ->
                    val target = requireNotNull(tag.toSeedOrNull()).poly
                    listOf(stellated.firstOrNull()?.poly, greatened.firstOrNull()?.poly).map { poly ->
                        poly?.let { candidate ->
                            val normalized = candidate.vs.map { it * (1.0 / candidate.circumradius) }
                            val targetNormalized = target.vs.map { it * (1.0 / target.circumradius) }
                            normalized.maxOf { point -> targetNormalized.minOf { other -> (point - other).norm } }
                        }
                    }
                }}",
        )
        assertEquals("GD", greatened.firstOrNull()?.poly?.recognizedSeedOrNull()?.tag)

    }

    @Test
    fun representativeNonCatalogConstellationProducesAUsefulExtension() = runTest {
        val source = Seed.Dodecahedron.poly.let { regular ->
            polyhedron {
                regular.vs.forEach { vertex ->
                    vertex(vertex.x * 1.2, vertex.y * 0.9, vertex.z * 1.1, vertex.kind)
                }
                regular.fs.forEach { face ->
                    face(face.fvs.map { vertex -> vertex.id }, face.kind)
                }
            }
        }
        assertEquals(null, source.recognizedSeedOrNull())
        val candidate = source.stellationCandidatesAsync(ConstellationOperation.Stellate).single().poly
        candidate.validateRenderableImmersion()
        assertEquals(null, candidate.recognizedSeedOrNull())
        assertEquals(source.analyzeSymmetry().pointGroup, candidate.analyzeSymmetry().pointGroup)
    }

    @Test
    fun workerPublishesDiscreteResultRangeAndRejectsUnavailableSelection() = runTest {
        val response = evaluateCore(CoreRequest(CoreState("D", listOf("S"), "c")))
        val range = response.transformTweakRanges.single().single()
        assertEquals(TransformTweak.StellationResult, range.tweak)
        assertEquals(1.0, range.min)
        assertEquals(1.0, range.max)
        assertEquals(listOf(1 to response.poly.fev()), range.options.map { it.value to it.fev })

        val unavailable = evaluateCore(CoreRequest(CoreState("D", listOf("S~l=2"), "c")))
        assertEquals(CoreIssueCode.TransformNotApplicable, unavailable.error?.code)
        assertEquals(true, unavailable.error?.detail.orEmpty().contains("unavailable"))

        val fractional = evaluateCore(CoreRequest(CoreState("D", listOf("S~l=1.5"), "c")))
        assertEquals(CoreIssueCode.TransformNotApplicable, fractional.error?.code)
        assertEquals(true, fractional.error?.detail.orEmpty().contains("integer"))
    }

    @Test
    fun classicalStellationSupportsEveryPresentationScaleAndPreservesPointGroup() = runTest {
        val sourceGroup = Seed.Dodecahedron.poly.analyzeSymmetry().pointGroup
        for (scaleTag in listOf("i", "m", "c")) {
            val response = evaluateCore(CoreRequest(CoreState("D", listOf("S"), scaleTag)))
            assertEquals(null, response.error, scaleTag)
            response.poly.validateRenderableImmersion()
            assertEquals(sourceGroup, response.symmetry.pointGroup, scaleTag)
        }
    }
}
