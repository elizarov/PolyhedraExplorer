package polyhedra.js.util

import kotlin.math.*

fun frac(x: Double) = x - floor(x)

infix fun Double.mod(m: Double): Double {
    val x = this / m
    return frac(x) * m
}
