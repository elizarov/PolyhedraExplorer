/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.main

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLElement
import polyhedra.web.components.PSlider
import polyhedra.web.components.observe
import polyhedra.web.poly.DEFAULT_PRINT_CHROMA
import polyhedra.web.poly.DEFAULT_PRINT_HUE
import polyhedra.web.poly.DEFAULT_PRINT_LIGHTNESS
import polyhedra.web.poly.PrintPreviewParams
import polyhedra.web.util.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal const val HUE_POINTER_RADIUS_EM = 4.55

internal data class PlaColorPreset(val name: String, val color: Color)

internal val plaColorPresets = listOf(
    PlaColorPreset("Red", oklchColor(DEFAULT_PRINT_LIGHTNESS, DEFAULT_PRINT_CHROMA, DEFAULT_PRINT_HUE)),
    PlaColorPreset("Orange", colorFromHex("#F26A21")),
    PlaColorPreset("Yellow", colorFromHex("#F2C230")),
    PlaColorPreset("Green", colorFromHex("#36A85B")),
    PlaColorPreset("Blue", colorFromHex("#2878C7")),
    PlaColorPreset("Purple", colorFromHex("#7E57C2")),
    PlaColorPreset("Pink", colorFromHex("#E64D8A")),
    PlaColorPreset("White", colorFromHex("#F4F1E8")),
    PlaColorPreset("Natural", colorFromHex("#E8DFC8")),
    PlaColorPreset("Gray", colorFromHex("#74777A")),
    PlaColorPreset("Black", colorFromHex("#1E2022")),
    PlaColorPreset("Silk gold", colorFromHex("#C99A3D")),
)

@Composable
fun PrintColorPopup(preview: PrintPreviewParams, onBack: () -> Unit) {
    preview.observe()
    val lightness = preview.lightness.targetValue
    val chroma = preview.chroma.targetValue
    val hue = preview.hue.targetValue
    val current = oklchColor(lightness, chroma, hue)

    Div(attrs = { classes("print-color-header") }) {
        Button(attrs = {
            classes("print-color-back")
            attr("aria-label", "Back to export")
            onClick { onBack() }
        }) { I(attrs = { classes("fa", "fa-arrow-left") }) }
        Span { Text("Print color") }
        Span(attrs = {
            classes("print-color-current")
            attr("style", "background-color: ${current.toHexString()}")
        })
        Span(attrs = { classes("print-color-hex") }) { Text(current.toHexString()) }
    }

    GroupHeader("Basic colors")
    Div(attrs = { classes("pla-color-presets") }) {
        for (preset in plaColorPresets) {
            val presetOklch = preset.color.toOklch()
            val selected = oklchDistance(lightness, chroma, hue, presetOklch) < 0.015
            Button(attrs = {
                classes("pla-color-preset", *(if (selected) arrayOf("selected") else emptyArray()))
                attr("aria-label", "Use ${preset.name} PLA")
                attr("title", preset.name)
                onClick { preview.updateColor(presetOklch) }
            }) {
                Span(attrs = {
                    classes("pla-color-swatch")
                    attr("style", "background-color: ${preset.color.toHexString()}")
                })
                Span(attrs = { classes("pla-color-name") }) { Text(preset.name) }
            }
        }
    }

    GroupHeader("Custom color · OKLCH")
    Div(attrs = { classes("hue-picker-area") }) {
        val pointer = huePointerOffset(hue)
        Button(attrs = {
            classes("hue-wheel")
            attr("aria-label", "Hue ${hue.toInt()} degrees")
            attr("style", "background: ${hueGradient(lightness, chroma)}")
            onClick { event ->
                val target = event.currentTarget as HTMLElement
                val bounds = target.getBoundingClientRect()
                val x = event.clientX - bounds.left - bounds.width / 2.0
                val y = event.clientY - bounds.top - bounds.height / 2.0
                if (hypot(x, y) > bounds.width * 0.17) {
                    preview.hue.updateValue(normalizeHue(atan2(y, x) * 180.0 / PI))
                }
            }
            onKeyDown { event ->
                val delta = when (event.key) {
                    "ArrowLeft", "ArrowDown" -> -1.0
                    "ArrowRight", "ArrowUp" -> 1.0
                    else -> return@onKeyDown
                }
                event.preventDefault()
                preview.hue.updateValue(normalizeHue(hue + delta))
            }
        }) {
            Span(attrs = {
                classes("hue-pointer")
                attr(
                    "style",
                    "left: calc(50% + ${pointer.first}em); top: calc(50% + ${pointer.second}em)",
                )
            })
            Span(attrs = {
                classes("hue-wheel-center")
                attr("style", "background-color: ${current.toHexString()}")
            })
        }
        Div(attrs = { classes("color-space-note") }) {
            Text("Hue chooses the filament family; chroma controls saturation and lightness controls brightness.")
        }
    }

    Div(attrs = { classes("print-color-components") }) {
        TableBody {
            ControlRow("Hue") { PSlider(preview.hue) }
            ControlRow("Chroma") { PSlider(preview.chroma) }
            ControlRow("Lightness") { PSlider(preview.lightness) }
        }
    }
}

internal fun huePointerOffset(hue: Double): Pair<Double, Double> {
    val angle = normalizeHue(hue) * PI / 180.0
    return HUE_POINTER_RADIUS_EM * cos(angle) to HUE_POINTER_RADIUS_EM * sin(angle)
}

private fun hueGradient(lightness: Double, chroma: Double): String =
    (0..12).joinToString(", ", prefix = "conic-gradient(from 90deg, ", postfix = ")") { index ->
        val hue = index * 30.0
        "${oklchColor(lightness, chroma, hue).toHexString()} ${index * 30}deg"
    }

private fun normalizeHue(hue: Double): Double = ((hue % 360.0) + 360.0) % 360.0

private fun oklchDistance(lightness: Double, chroma: Double, hue: Double, other: Oklch): Double {
    val angle = normalizeHue(hue - other.hue).let { if (it > 180.0) it - 360.0 else it } * PI / 180.0
    val hueDistance = 2.0 * minOf(chroma, other.chroma) * kotlin.math.sin(angle / 2.0)
    return hypot(hypot(lightness - other.lightness, chroma - other.chroma), hueDistance)
}
