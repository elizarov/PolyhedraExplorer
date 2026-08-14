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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

        assertEquals(27, candidates.size)
        assertTrue(duration < 3.seconds, "Deltoidal hexecontahedron stellation took $duration")
        candidates.forEach { candidate ->
            candidate.poly.validateRenderableImmersion()
            candidate.poly.resolved().validateProperGeometry()
        }
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
        assertEquals(27.0, response.transformTweakRanges.single().single().max)
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
