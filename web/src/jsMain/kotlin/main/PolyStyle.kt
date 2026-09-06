/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.main

import polyhedra.model.poly.*
import polyhedra.model.util.*
import polyhedra.web.util.*

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
    return hslColor(hue0 + phase + rem.toDouble() / count, 0.8, 0.5)
}

object PolyStyle {
    private val overlapPalettes = linkedMapOf<Polyhedron, MutableMap<List<FaceKind>, Int>>()
    val edgeColor = hslColor(0.0, 0.0, 0.1)
    val selectionColor = hslColor(0.92, 0.95, 0.55)
    val symmetryPlaneColor = hslColor(0.53, 0.9, 0.55, 0.22)
    val symmetryAxisColor = hslColor(0.0, 0.0, 0.0)
    fun faceColor(f: Face): Color =
        paletteColor(f.kind.id)
    /** A shared, unused palette entry for each unordered set of overlapping orbits. */
    fun faceColor(poly: Polyhedron, sourceFaceIds: List<Int>): Color {
        val kinds = sourceFaceIds.map { poly.fs[it].kind }.distinct().sorted()
        if (kinds.size == 1) return paletteColor(kinds.single().id)
        val palette = overlapPalettes.getOrPut(poly) {
            if (overlapPalettes.size >= 8) overlapPalettes.remove(overlapPalettes.keys.first())
            val combinations = poly.coplanarFaces.map { patch ->
                patch.sourceFaceIds.map { poly.fs[it].kind }.distinct().sorted()
            }.filter { it.size > 1 }.distinct().sortedBy { it.joinToString(",") { kind -> kind.id.toString() } }
            combinations.withIndex().associateTo(linkedMapOf()) { it.value to it.index }
        }
        val index = palette.getOrPut(kinds) { palette.size }
        return paletteColor(poly.faceKinds.keys.maxOf { it.id } + 1 + index)
    }
    fun vertexColor(v: Vertex): Color =
        paletteColor(v.kind.id)
}

enum class Display(override val tag: String) : Tagged {
    All("a"),
    Faces("f"),
    Edges("e")
}

val Displays: List<Display> = Display.entries

fun Display.hasFaces() = this != Display.Edges
fun Display.hasEdges() = this != Display.Faces

