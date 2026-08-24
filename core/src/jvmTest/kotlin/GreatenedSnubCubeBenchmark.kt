package polyhedra.core

import kotlinx.coroutines.runBlocking
import polyhedra.core.poly.Seed
import polyhedra.core.poly.SnubCube
import polyhedra.core.transform.ConstellationOperation
import polyhedra.core.transform.clearStellationCandidateCache
import polyhedra.core.transform.stellationCandidatesAsync
import polyhedra.model.poly.FEV
import polyhedra.model.poly.fev
import kotlin.math.roundToLong
import kotlin.time.TimeSource

/** Focused cold-construction benchmark; normal result switching uses the constellation cache. */
fun main(args: Array<String>) = runBlocking {
    val warmups = args.getOrNull(0)?.toIntOrNull() ?: 1
    val samples = args.getOrNull(1)?.toIntOrNull() ?: 5
    val expectedFevs = listOf(FEV(38, 240, 158), FEV(38, 252, 156))
    var checksum = 0L

    suspend fun measure(): Long {
        clearStellationCandidateCache()
        val mark = TimeSource.Monotonic.markNow()
        val candidates = Seed.SnubCube.poly.stellationCandidatesAsync(ConstellationOperation.Greaten)
        val elapsed = mark.elapsedNow().inWholeNanoseconds
        check(candidates.map { candidate -> candidate.poly.fev() } == expectedFevs) {
            "Greatened Snub Cube candidates changed: ${candidates.map { candidate -> candidate.poly.fev() }}"
        }
        checksum += candidates.sumOf { candidate ->
            val fev = candidate.poly.fev()
            fev.f.toLong() * 1_000_000L + fev.e * 1_000L + fev.v
        }
        return elapsed
    }

    repeat(warmups) { measure() }
    val timings = LongArray(samples) { measure() }.sorted()
    val median = if (samples % 2 == 1) {
        timings[samples / 2]
    } else {
        ((timings[samples / 2 - 1] + timings[samples / 2]) / 2.0).roundToLong()
    }
    val candidateFevs = Seed.SnubCube.poly
        .stellationCandidatesAsync(ConstellationOperation.Greaten)
        .map { candidate -> candidate.poly.fev() }
    println("BENCHMARK name=greaten/snub-cube mode=cold format=ns/op")
    println(
        "RESULT medianNs=$median minNs=${timings.first()} maxNs=${timings.last()} " +
            "samples=$samples candidates=${candidateFevs.size} fev=$candidateFevs",
    )
    println("CHECKSUM $checksum")
}
