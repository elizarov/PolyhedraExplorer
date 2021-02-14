package polyhedra.common

import kotlin.math.*

const val EPS = 1e-12

infix fun Double.approx(x: Double): Boolean = abs(this - x) < EPS

fun sqr(x: Double): Double = x * x

fun <T> Iterable<T>.avgOf(selector: (T) -> Double): Double {
    var n = 0
    var sum = 0.0
    for (e in this) {
        n++
        sum += selector(e)
    }
    return sum / n
}

fun <T, R> List<T>.zipWithNextCycle(transform: (T, T) -> R): List<R> = List(size) { i ->
    transform(this[i], this[(i + 1) % size])
}

