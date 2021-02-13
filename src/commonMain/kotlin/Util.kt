package polyhedra.common

import kotlin.math.*

const val EPS = 1e-12

infix fun Double.approx(x: Double): Boolean = abs(this - x) < EPS

fun sqr(x: Double): Double = x * x

fun <T> Iterable<T>.groupByToList(keySelector: (T) -> Int): List<List<T>> {
    val result = ArrayList<ArrayList<T>>()
    for (e in this) {
        val k = keySelector(e)
        check(k >= 0)
        while (result.size <= k) result.add(ArrayList())
        result[k].add(e)
    }
    return result
}

fun <T> Iterable<T>.avgOf(selector: (T) -> Double): Double {
    var n = 0
    var sum = 0.0
    for (e in this) {
        n++
        sum += selector(e)
    }
    return sum / n
}