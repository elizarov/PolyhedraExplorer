package polyhedra.js

import polyhedra.common.*
import kotlin.math.*

data class Color(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1.0f
) {
    override fun toString(): String =
        "Color(${r.fmt(3)}, ${g.fmt(3)}, ${b.fmt(3)}, ${a.fmt(3)})"
}

fun hsvColor(h: Double, s: Double, v: Double, a: Double = 1.0): Color {
    val c = v * s // chroma
    val p = frac(h) * 6
    val x = c * (1 - abs((p mod 2.0) - 1))
    val m = (v - c).toFloat()
    val cm = (c + m).toFloat()
    val xm = (x + m).toFloat()
    val af = a.toFloat()
    return when (p.toInt()) {
        0 -> Color(cm, xm, m, af)
        1 -> Color(xm, cm, m, af)
        2 -> Color(m, cm, xm, af)
        3 -> Color(m, xm, cm, af)
        4 -> Color(xm, m, cm, af)
        else -> Color(cm, m, xm, af)
    }
}

private infix fun Double.mod(m: Double): Double {
    val x = this / m
    return frac(x) * m
}

private fun frac(x: Double) = x - floor(x)

fun String.parseCSSColor(): Color? {
    val match = Regex("rgba?\\((\\d+), (\\d+), (\\d+)(?:, (\\d+))?\\)").matchEntire(this)
        ?: return null
    val (r, g, b, a) = match.groupValues.drop(1).map {
        if (it.isEmpty()) 1.0f else it.toInt() / 255.0f
    }
    return Color(r, g, b, a)
}