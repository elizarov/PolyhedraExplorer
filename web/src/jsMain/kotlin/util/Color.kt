/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.util

import org.khronos.webgl.*
import polyhedra.model.util.*
import polyhedra.web.glsl.*
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

/** A perceptually uniform color expressed in the OKLCH color space. */
data class Oklch(
    val lightness: Double,
    val chroma: Double,
    val hue: Double,
)

fun hslColor(h: Double, s: Double, l: Double, a: Double = 1.0): Color {
    val c = (1 -  (2 * l - 1).absoluteValue) * s // chroma
    val p = frac(h) * 6
    val x = c * (1 - abs((p mod 2.0) - 1))
    val m = (l - c / 2).toFloat()
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

fun Color.toFloat32Array4(): Float32Array = float32Of(r, g, b, a)

fun Color.toRgbString() =
    "rgb(${r.intColor},${g.intColor},${b.intColor})"

fun Color.toHexString(): String =
    "#${r.hexColor}${g.hexColor}${b.hexColor}"

fun colorFromHex(hex: String): Color {
    val value = hex.removePrefix("#")
    require(value.length == 6) { "Expected a six-digit RGB color, got $hex" }
    return Color(
        value.substring(0, 2).toInt(16) / 255.0f,
        value.substring(2, 4).toInt(16) / 255.0f,
        value.substring(4, 6).toInt(16) / 255.0f,
    )
}

/**
 * Converts OKLCH to display-ready sRGB. Colors outside the sRGB gamut are mapped by reducing
 * chroma while preserving perceptual lightness and hue.
 */
fun oklchColor(lightness: Double, chroma: Double, hue: Double, alpha: Double = 1.0): Color {
    val normalizedLightness = lightness.coerceIn(0.0, 1.0)
    val normalizedChroma = chroma.coerceAtLeast(0.0)
    val normalizedHue = ((hue % 360.0) + 360.0) % 360.0

    fun convert(candidateChroma: Double): Rgb {
        val angle = normalizedHue * PI / 180.0
        val a = candidateChroma * cos(angle)
        val b = candidateChroma * sin(angle)
        val lRoot = normalizedLightness + 0.3963377774 * a + 0.2158037573 * b
        val mRoot = normalizedLightness - 0.1055613458 * a - 0.0638541728 * b
        val sRoot = normalizedLightness - 0.0894841775 * a - 1.2914855480 * b
        val l = lRoot * lRoot * lRoot
        val m = mRoot * mRoot * mRoot
        val s = sRoot * sRoot * sRoot
        return Rgb(
            linearToSrgb(+4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
            linearToSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
            linearToSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
        )
    }

    var rgb = convert(normalizedChroma)
    if (!rgb.inGamut) {
        var low = 0.0
        var high = normalizedChroma
        repeat(16) {
            val middle = (low + high) / 2.0
            val candidate = convert(middle)
            if (candidate.inGamut) {
                low = middle
                rgb = candidate
            } else {
                high = middle
            }
        }
    }
    return Color(
        rgb.r.coerceIn(0.0, 1.0).toFloat(),
        rgb.g.coerceIn(0.0, 1.0).toFloat(),
        rgb.b.coerceIn(0.0, 1.0).toFloat(),
        alpha.coerceIn(0.0, 1.0).toFloat(),
    )
}

fun Oklch.toColor(alpha: Double = 1.0): Color = oklchColor(lightness, chroma, hue, alpha)

fun Color.toOklch(): Oklch {
    val red = srgbToLinear(r.toDouble().coerceIn(0.0, 1.0))
    val green = srgbToLinear(g.toDouble().coerceIn(0.0, 1.0))
    val blue = srgbToLinear(b.toDouble().coerceIn(0.0, 1.0))
    val l = (0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue).pow(1.0 / 3.0)
    val m = (0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue).pow(1.0 / 3.0)
    val s = (0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue).pow(1.0 / 3.0)
    val lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val b = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    val chroma = hypot(a, b)
    val hue = if (chroma < 1e-9) 0.0 else ((atan2(b, a) * 180.0 / PI) + 360.0) % 360.0
    return Oklch(lightness, chroma, hue)
}

private data class Rgb(val r: Double, val g: Double, val b: Double) {
    val inGamut: Boolean
        get() = r in 0.0..1.0 && g in 0.0..1.0 && b in 0.0..1.0
}

private fun linearToSrgb(value: Double): Double =
    if (value <= 0.0031308) 12.92 * value else 1.055 * value.pow(1.0 / 2.4) - 0.055

private fun srgbToLinear(value: Double): Double =
    if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

private val Float.intColor
    get() = (coerceIn(0.0f, 1.0f) * 255).roundToInt()

private val Float.hexColor
    get() = intColor.toString(16).padStart(2, '0').uppercase()
