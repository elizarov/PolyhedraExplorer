package polyhedra.js

import polyhedra.common.*
import polyhedra.js.util.*
import kotlin.math.*

private val hue0 = 57.0 / 360
private val ratio = (sqrt(5.0) - 1) / 2

private fun paletteColor(id: Int) =
    hsvColor(hue0 + ratio * id, 0.9, 0.95)

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

