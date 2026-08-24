package polyhedra.core

import kotlinx.coroutines.runBlocking
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.RhombicTriacontahedron
import polyhedra.core.poly.Seed
import polyhedra.core.transform.ConstellationOperation
import polyhedra.core.transform.clearStellationCandidateCache
import polyhedra.core.transform.stellationCandidatesAsync
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.poly.fev
import kotlin.math.roundToLong
import kotlin.time.TimeSource

/** Focused cold benchmark for full generic enumeration of all 33 supported results. */
fun main(args: Array<String>) = runBlocking {
    val warmups = args.getOrNull(0)?.toIntOrNull() ?: 1
    val samples = args.getOrNull(1)?.toIntOrNull() ?: 5
    require(warmups >= 0 && samples > 0)
    var checksum = 0L

    suspend fun measure(): Long {
        clearStellationCandidateCache()
        val mark = TimeSource.Monotonic.markNow()
        val candidates = Seed.RhombicTriacontahedron.poly
            .stellationCandidatesAsync(ConstellationOperation.Greaten)
        val elapsed = mark.elapsedNow().inWholeNanoseconds
        check(candidates.size == 33) { "Expected 33 candidates, found ${candidates.size}" }
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
    val candidates = Seed.RhombicTriacontahedron.poly
        .stellationCandidatesAsync(ConstellationOperation.Greaten)
    val response = evaluateCore(
        CoreRequest(
            CoreState("daD", listOf("G~l=7"), "c"),
            rimWidth = 0.05,
            faceWidth = 0.10,
        ),
    )
    println("BENCHMARK name=greaten/rhombic-triacontahedron mode=cold format=ns/op")
    println(
        "RESULT medianNs=$median minNs=${timings.first()} maxNs=${timings.last()} " +
            "samples=$samples candidates=${candidates.size} seventhFev=${candidates[6].fev}",
    )
    println("SEVENTH error=${response.error} fev=${response.poly.fev()}")
    println("CHECKSUM $checksum")
}
