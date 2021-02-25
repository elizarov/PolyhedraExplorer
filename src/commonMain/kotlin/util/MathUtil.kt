package polyhedra.common.util

import kotlin.math.*

const val EPS = 1e-12

infix fun Double.approx(x: Double): Boolean = abs(this - x) < EPS

infix fun Double.angleApprox(x: Double): Boolean = abs(this - x) mod (2 * PI) < EPS

fun sqr(x: Double): Double = x * x

fun frac(x: Double) = x - floor(x)

infix fun Double.mod(m: Double): Double {
    val x = this / m
    return frac(x) * m
}
