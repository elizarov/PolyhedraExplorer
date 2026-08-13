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
import polyhedra.model.api.parseTransformTag
import polyhedra.model.poly.fev
import polyhedra.model.poly.FEV
import polyhedra.model.poly.Polyhedron
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FacePlaneConstellationTest {
    @Test
    fun dodecahedronMainLineContainsEveryClassicalStellationInOrder() = runTest {
        val candidates = Seed.Dodecahedron.poly
            .stellationCandidatesAsync(ConstellationOperation.Stellate)

        assertEquals(
            listOf("SD", "GD", "GSD"),
            candidates.map { candidate -> candidate.poly.recognizedSeedOrNull()?.tag },
            candidates.map { candidate -> candidate.fev }.toString(),
        )
        assertEquals(listOf(1, 2, 3), candidates.map { candidate -> candidate.stratum })
        candidates.forEach { candidate -> candidate.poly.assertKindsMatchGeometricOrbits() }
    }

    @Test
    fun icosahedronMainLineFiltersCompoundsAndKeepsEverySupportedStratum() = runTest {
        val candidates = Seed.Icosahedron.poly
            .stellationCandidatesAsync(ConstellationOperation.Stellate)

        assertEquals(6, candidates.size, candidates.map { candidate ->
            Triple(candidate.fev, candidate.poly.recognizedSeedOrNull()?.tag, candidate.poly.isConvexGeometry)
        }.toString())
        assertEquals(listOf(1, 3, 4, 5, 6, 7), candidates.map { candidate -> candidate.stratum })
        assertEquals(1, candidates.count { candidate -> candidate.poly.recognizedSeedOrNull()?.tag == "GI" })
        assertTrue(candidates.all { candidate -> runCatching { candidate.poly.validateRenderableImmersion() }.isSuccess })
        assertTrue(candidates.all { candidate -> candidate.poly.surfaceComponentCountForTest() == 1 })
        candidates.forEach { candidate -> candidate.poly.assertKindsMatchGeometricOrbits() }
    }

    @Test
    fun secondIcosahedronStellationPublishesItsDistinctFaceOrbits() = runTest {
        val response = evaluateCore(CoreRequest(CoreState("I", listOf("S~l=2"), "c")))

        assertEquals(null, response.error)
        assertEquals("Stellated 2 Icosahedron", response.polyName)
        assertEquals(FEV(3, 5, 4), response.symmetry.orbitCounts)
        assertEquals(response.symmetry.orbitCounts.f, response.poly.faceKinds.size)
        assertEquals(response.symmetry.orbitCounts.e, response.poly.edgeKinds.size)
        assertEquals(response.symmetry.orbitCounts.v, response.poly.vertexKinds.size)
    }

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
        greatened.forEach { candidate -> candidate.poly.assertKindsMatchGeometricOrbits() }

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
        val candidate = source.stellationCandidatesAsync(ConstellationOperation.Stellate).first().poly
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
        assertEquals(3.0, range.max)
        assertEquals(listOf(1, 2, 3), range.options.map { it.value })
        assertEquals(response.poly.fev(), range.options.first().fev)

        val unavailable = evaluateCore(CoreRequest(CoreState("D", listOf("S~l=4"), "c")))
        assertEquals(CoreIssueCode.TransformNotApplicable, unavailable.error?.code)
        assertEquals(true, unavailable.error?.detail.orEmpty().contains("unavailable"))

        val fractional = evaluateCore(CoreRequest(CoreState("D", listOf("S~l=1.5"), "c")))
        assertEquals(CoreIssueCode.TransformNotApplicable, fractional.error?.code)
        assertEquals(true, fractional.error?.detail.orEmpty().contains("integer"))
    }

    @Test
    fun workerPublishesOnlySupportedIcosahedronMainLineResults() = runTest {
        val response = evaluateCore(CoreRequest(CoreState("I", listOf("S"), "c")))
        val range = response.transformTweakRanges.single().single()

        assertEquals(1.0, range.min)
        assertEquals(6.0, range.max)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), range.options.map { option -> option.value })
        assertEquals(response.poly.fev(), range.options.first().fev)

        val farthest = evaluateCore(CoreRequest(CoreState("I", listOf("S~l=6"), "c")))
        assertEquals(null, farthest.error)
        assertEquals(6.0, farthest.validTransformTags.single().parseTransformTag()?.tweaks?.get(TransformTweak.StellationResult))
        farthest.poly.validateRenderableImmersion()
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

private fun Polyhedron.assertKindsMatchGeometricOrbits() {
    val orbits = analyzeSymmetry().orbitCounts
    assertEquals(orbits.f, faceKinds.size, "face kinds for $orbits / ${fev()}")
    assertEquals(orbits.v, vertexKinds.size, "vertex kinds for $orbits / ${fev()}")
}

private fun Polyhedron.surfaceComponentCountForTest(): Int {
    val visited = hashSetOf<Int>()
    var components = 0
    for (first in fs) {
        if (first.id in visited) continue
        components++
        val pending = ArrayDeque<Int>()
        pending += first.id
        while (pending.isNotEmpty()) {
            val face = fs[pending.removeFirst()]
            if (!visited.add(face.id)) continue
            face.directedEdges.mapTo(pending) { edge -> edge.l.id }
        }
    }
    return components
}
