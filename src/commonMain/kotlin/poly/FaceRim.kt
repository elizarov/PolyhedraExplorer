package polyhedra.common.poly

import polyhedra.common.util.*
import kotlin.math.*

private const val MAX_RIM_FRACTION = 0.8

class FaceRim(f: Face)  {
    val evs = List(f.size) { i ->
        val j = (i + 1) % f.size
        (f[j] - f[i]).unit
    }

    val rimDir = List(f.size) { i ->
        val k = (i + f.size - 1) % f.size
        val a = evs[i]
        val b = evs[k]
        val c = 1 - a * b
        (a - b) / sqrt(2 * c - c * c)
    }

    val maxRim = run {
        var maxRim = Double.POSITIVE_INFINITY
        for (i in 0 until f.size) {
            val j = (i + 1) % f.size
            maxRim = minOf(maxRim, (f[j] - f[i]).norm / (evs[i] * rimDir[i] - evs[i] * rimDir[j]))
        }
        maxRim *= MAX_RIM_FRACTION
        maxRim
    }

    val borderNorm = List(f.size) { i ->
        val j = (i + 1) % f.size
        (f[j] cross f[i]).unit
    }
}