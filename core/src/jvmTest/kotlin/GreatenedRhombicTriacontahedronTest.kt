package polyhedra.core

import kotlinx.coroutines.runBlocking
import polyhedra.core.poly.RhombicTriacontahedron
import polyhedra.core.poly.Seed
import polyhedra.core.transform.ConstellationOperation
import polyhedra.core.transform.clearStellationCandidateCache
import polyhedra.core.transform.resolved
import polyhedra.core.transform.stellationCandidatesAsync
import kotlin.test.Test
import kotlin.test.assertEquals

class GreatenedRhombicTriacontahedronTest {
    @Test
    fun everyPublishedGreateningHasAValidResolvedPhysicalBoundary() = runBlocking {
        clearStellationCandidateCache()
        val candidates = Seed.RhombicTriacontahedron.poly
            .stellationCandidatesAsync(ConstellationOperation.Greaten)
        assertEquals(33, candidates.size)

        val failures = candidates.mapIndexedNotNull { index, candidate ->
            // resolved() validates the constructed result as an embedded proper boundary.
            runCatching { candidate.poly.resolved(null, candidate.geometryAnalysis) }
                .exceptionOrNull()
                ?.let { failure -> "$index ${candidate.fev}: ${failure.message}" }
        }
        assertEquals(emptyList(), failures)
    }
}
