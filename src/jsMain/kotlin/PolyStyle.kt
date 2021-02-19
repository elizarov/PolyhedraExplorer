package polyhedra.js

import polyhedra.common.*
import kotlin.math.*

private val hue0 = 57.0 / 360
private val ratio = (sqrt(5.0) - 1) / 2

private fun paletteColor(id: Int) =
    hsvColor(hue0 + ratio * id, 0.9, 0.95)

class PolyStyle {
    fun faceColor(f: Face): Color = paletteColor(f.kind.id)
}

