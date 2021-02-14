package polyhedra.js

import kotlin.math.*

data class Color(
    val r: Double,
    val g: Double,
    val b: Double,
    val a: Double = 1.0
)

fun hsvColor(h: Double, s: Double, v: Double, a: Double = 1.0): Color {
    val c = v * s // chroma
    val p = frac(h) * 6
    val x = c * (1 - abs((p mod 2.0) - 1))
    val m = v - c
    val cm = c + m
    val xm = x + m
    return when (p.toInt()) {
        0 -> Color(cm, xm, m, a)
        1 -> Color(xm, cm, m, a)
        2 -> Color(m, cm, xm, a)
        3 -> Color(m, xm, cm, a)
        4 -> Color(xm, m, cm, a)
        else -> Color(cm, m, xm, a)
    }
}

private infix fun Double.mod(m: Double): Double {
    val x = this / m
    return frac(x) * m
}

private fun frac(x: Double) = x - floor(x)