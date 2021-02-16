package polyhedra.common

import kotlin.math.*

const val EPS = 1e-12

infix fun Double.approx(x: Double): Boolean = abs(this - x) < EPS

fun sqr(x: Double): Double = x * x

inline fun <T> Iterable<T>.avgOf(selector: (T) -> Double): Double {
    var n = 0
    var sum = 0.0
    for (e in this) {
        n++
        sum += selector(e)
    }
    return sum / n
}

inline fun <T, R> List<T>.zipWithCycle(transform: (T, T) -> R): List<R> = List(size) { i ->
    transform(this[i], this[(i + 1) % size])
}

fun <T> List<T>.updatedAt(index: Int, value: T): List<T> {
    val result = toMutableList()
    result[index] = value
    return result
}

fun <T> List<T>.removedAt(index: Int): List<T> {
    val result = toMutableList()
    result.removeAt(index)
    return result
}

inline fun <T, R> Sequence<T>.distinctIndexed(transform: (Int) -> R): Map<T, R> {
    val result = mutableMapOf<T, R>()
    var index = 0
    for (e in this) {
        if (e !in result) result[e] = transform(index++)
    }
    return result
}