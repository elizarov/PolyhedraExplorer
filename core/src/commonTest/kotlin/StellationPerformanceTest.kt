package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.DeltoidalHexecontahedron
import polyhedra.core.poly.Seed
import polyhedra.core.poly.validateProperGeometry
import polyhedra.core.poly.validateRenderableImmersion
import polyhedra.core.transform.ConstellationOperation
import polyhedra.core.transform.clearStellationCandidateCache
import polyhedra.core.transform.stellationCandidatesAsync
import polyhedra.core.transform.resolved
import polyhedra.model.api.CoreProgress
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.poly.Scale
import polyhedra.model.poly.fev
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class StellationPerformanceTest {
    @Test
    fun deltoidalHexecontahedronStellationCompletesQuickly() = runTest {
        clearStellationCandidateCache()
        val (candidates, duration) = measureTimedValue {
            Seed.DeltoidalHexecontahedron.poly
                .stellationCandidatesAsync(ConstellationOperation.Stellate)
        }

        assertEquals(30, candidates.size)
        assertTrue(duration < 3.seconds, "Deltoidal hexecontahedron stellation took $duration")
        candidates.forEach { candidate ->
            candidate.poly.validateRenderableImmersion()
            candidate.poly.resolved(null, candidate.geometryAnalysis).validateProperGeometry()
        }

        val current = CoreState("deD", listOf("S~l=15"), "c")
        val previous = CoreState("deD", listOf("S~l=14"), "c")
        val progress = arrayListOf<CoreProgress>()
        val (response, switchDuration) = measureTimedValue {
            evaluateCore(
                CoreRequest(current, previous, 0.5, detectSeed = true, rimWidth = 0.05, faceWidth = 0.1),
                progress::add,
            )
        }

        assertNull(response.error)
        assertEquals(candidates[14].fev, response.poly.fev())
        assertSame(candidates[14].geometryAnalysis, response.geometryAnalysis)
        assertSame(candidates[14].symmetry, response.symmetry)
        assertSame(candidates[14].availableOrbitTransformTags, response.availableOrbitTransforms.last())
        assertSame(candidates[14].resolvedRims(Scale.Circumradius, 0.05, 0.1), response.resolvedRims)
        assertTrue(response.animation.isEmpty(), "Changing the discrete Result must not evaluate an unused animation")
        assertTrue(switchDuration < 2.seconds, "Cached Result 15 switch took $switchDuration")
        assertEquals(100, progress.last().done)

        val (repeated, repeatDuration) = measureTimedValue {
            evaluateCore(CoreRequest(current, detectSeed = true, rimWidth = 0.05, faceWidth = 0.1))
        }
        assertSame(response.geometryAnalysis, repeated.geometryAnalysis)
        assertSame(response.symmetry, repeated.symmetry)
        assertSame(response.resolvedRims, repeated.resolvedRims)
        assertTrue(repeatDuration < 1.seconds, "Repeated Result 15 response took $repeatDuration")
    }

    @Test
    fun workerReportsMonotonicIntermediateStellationProgress() = runTest {
        clearStellationCandidateCache()
        val progress = arrayListOf<CoreProgress>()
        val response = evaluateCore(
            CoreRequest(CoreState("deD", listOf("S"), "c")),
            progress::add,
        )

        assertNull(response.error)
        assertEquals(30.0, response.transformTweakRanges.single().single().max)
        val done = progress.filter { item -> item.transformIndex == 0 }.map(CoreProgress::done)
        assertEquals(0, done.first())
        assertEquals(1, done.first { value -> value > 0 })
        assertEquals(100, done.last())
        assertTrue(done.zipWithNext().all { (previous, next) -> previous <= next }, done.toString())
        assertTrue(done.any { value -> value in 1..94 }, done.toString())
        assertTrue(done.distinct().size >= 20, done.toString())
    }

    @Test
    fun workerReportsMonotonicIntermediateGreateningProgress() = runTest {
        clearStellationCandidateCache()
        val progress = arrayListOf<CoreProgress>()
        val response = evaluateCore(
            CoreRequest(CoreState("D", listOf("G"), "c")),
            progress::add,
        )

        assertNull(response.error)
        assertEquals(3.0, response.transformTweakRanges.single().single().max)
        val done = progress.filter { item -> item.transformIndex == 0 }.map(CoreProgress::done)
        assertEquals(0, done.first())
        assertEquals(1, done.first { value -> value > 0 })
        assertEquals(100, done.last())
        assertTrue(done.zipWithNext().all { (previous, next) -> previous <= next }, done.toString())
        assertTrue(done.any { value -> value in 1..94 }, done.toString())
        assertTrue(done.distinct().size >= 10, done.toString())
    }
}
