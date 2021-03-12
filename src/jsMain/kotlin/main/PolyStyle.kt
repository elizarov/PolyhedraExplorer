/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.main

import polyhedra.common.poly.*
import polyhedra.common.util.*
import polyhedra.js.util.*

private const val hue0 = 57.0 / 360
private const val divisor = 4

private fun paletteColor(id: Int): Color {
    var phase = 0.0
    var count = divisor
    var rem = id
    while (rem >= count) {
        rem -= count
        if (phase > 0) count *= 2
        phase += 0.5 / count
    }
    return hsvColor(hue0 + phase + rem.toDouble() / count, 0.8, 0.95)
}

object PolyStyle {
    val edgeColor = hsvColor(0.0, 0.0, 0.1)
    fun faceColor(f: Face, dual: Boolean): Color =
        paletteColor(if (dual) f.dualKind.id else f.kind.id)
}

enum class Display(override val tag: String) : Tagged {
    All("a"),
    Faces("f"),
    Edges("e")
}

val Displays = Display.values().toList()

fun Display.hasFaces() = this != Display.Edges
fun Display.hasEdges() = this != Display.Faces

