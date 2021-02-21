package polyhedra.js.util

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.common.util.*
import polyhedra.js.glsl.*
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

fun Color.toFloat32Array(): Float32Array = float32Of(r, g, b, a)