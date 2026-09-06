package polyhedra.core

import kotlin.time.Duration

/** Functional regressions share their measured workloads with the opt-in JVM benchmark. */
internal data class AlgorithmTiming(val name: String, val elapsed: Duration, val budget: Duration) {
    override fun toString(): String = "$name: $elapsed (budget $budget)"
}
