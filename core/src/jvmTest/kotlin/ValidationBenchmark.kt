package polyhedra.core

import kotlinx.coroutines.runBlocking

/** Run on a quiet machine: hardware-dependent budgets are not shared-runner release gates. */
fun main() = runBlocking {
    val timings = buildList {
        addAll(StellationPerformanceTest().measureDeltoidalStellation())
        val stl = StlApiTest()
        add(stl.measureStarAntiprismStl())
        add(stl.measureHiddenGreatenedStl())
    }
    timings.forEach(::println)
    val exceeded = timings.filter { it.elapsed >= it.budget }
    check(exceeded.isEmpty()) { "Performance budgets exceeded:\n${exceeded.joinToString("\n")}" }
}
