package polyhedra.benchmarks

import polyhedra.common.poly.*
import polyhedra.common.transform.bevelled
import polyhedra.common.transform.canonical
import polyhedra.common.transform.cantellated
import polyhedra.common.transform.chamfered
import polyhedra.common.transform.snub
import polyhedra.common.transform.truncated
import kotlin.math.roundToLong
import kotlin.time.TimeSource

expect val benchmarkPlatform: String

private data class BenchmarkCase(
    val name: String,
    val operationsPerSample: Int,
    val warmups: Int = 3,
    val samples: Int = 12,
    val prepare: () -> Polyhedron,
    val operation: suspend (Polyhedron) -> Polyhedron,
)

private var checksum = 0.0

suspend fun main() {
    val cases = listOf(
        BenchmarkCase("truncate/icosahedron", 200, prepare = { fresh(Seed.Icosahedron.poly) }) { it.truncated() },
        BenchmarkCase("cantellate/dodecahedron", 100, prepare = { fresh(Seed.Dodecahedron.poly) }) { it.cantellated() },
        BenchmarkCase("bevel/dodecahedron", 100, prepare = { fresh(Seed.Dodecahedron.poly) }) { it.bevelled() },
        BenchmarkCase("snub/dodecahedron", 50, prepare = { fresh(Seed.Dodecahedron.poly) }) { it.snub() },
        BenchmarkCase("chamfer/icosahedron", 100, prepare = { fresh(Seed.Icosahedron.poly) }) { it.chamfered() },
        BenchmarkCase(
            "canonical/irregular-truncated-cube",
            operationsPerSample = 1,
            warmups = 5,
            samples = 15,
            prepare = { fresh(Seed.Cube.poly).truncated(0.2) },
        ) { it.canonical(progress = null) },
    )

    println("BENCHMARK platform=$benchmarkPlatform format=ns/op")
    for (case in cases) {
        repeat(case.warmups) {
            val inputs = List(case.operationsPerSample) { case.prepare() }
            repeat(case.operationsPerSample) { consume(case.operation(inputs[it])) }
        }
        val timings = LongArray(case.samples) {
            val inputs = List(case.operationsPerSample) { case.prepare() }
            val mark = TimeSource.Monotonic.markNow()
            repeat(case.operationsPerSample) { consume(case.operation(inputs[it])) }
            mark.elapsedNow().inWholeNanoseconds / case.operationsPerSample
        }.sorted()
        val median = if (timings.size % 2 == 1) {
            timings[timings.size / 2]
        } else {
            ((timings[timings.size / 2 - 1] + timings[timings.size / 2]) / 2.0).roundToLong()
        }
        println(
            "RESULT name=${case.name} medianNs=$median minNs=${timings.first()} " +
                "maxNs=${timings.last()} samples=${case.samples}",
        )
    }
    println("CHECKSUM ${checksum.roundToLong()}")
}

private fun fresh(poly: Polyhedron): Polyhedron =
    polyhedronCopy(poly.vs, poly.fs, poly.faceKindSources)

private fun consume(poly: Polyhedron) {
    checksum += poly.vs.size + poly.es.size + poly.fs.size + poly.circumradius
}
