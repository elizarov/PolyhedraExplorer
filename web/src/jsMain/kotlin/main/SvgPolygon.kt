/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.main

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Span
import polyhedra.model.poly.Edge
import polyhedra.model.poly.PolygonProjection
import polyhedra.model.poly.computeNetProjection
import polyhedra.model.util.fmt
import polyhedra.web.util.Color
import polyhedra.web.util.toRgbString

@Composable
fun SvgPolygon(classes: String, figure: PolygonProjection, stroke: Color, fill: Color) {
    SvgPolygons(classes, stroke, listOf(ColoredPolygon(figure, fill)))
}

@Composable
internal fun SvgEdgeNet(classes: String, edge: Edge, stroke: Color) {
    val net = edge.computeNetProjection()
    SvgPolygons(
        classes,
        stroke,
        listOf(
            ColoredPolygon(net.left.figure, PolyStyle.faceColor(net.left.face), "left", net.left.face.kind.toString()),
            ColoredPolygon(net.right.figure, PolyStyle.faceColor(net.right.face), "right", net.right.face.kind.toString()),
        ),
    )
}

private data class ColoredPolygon(
    val figure: PolygonProjection,
    val fill: Color,
    val side: String? = null,
    val faceKind: String? = null,
)

@Composable
private fun SvgPolygons(classes: String, stroke: Color, polygons: List<ColoredPolygon>) {
    val vertices = polygons.flatMap { it.figure.vs }
    val x0 = vertices.minOf { it.x }
    val y0 = vertices.minOf { it.y }
    val w0 = vertices.maxOf { it.x } - x0
    val h0 = vertices.maxOf { it.y } - y0
    val strokeWidth = polygons.maxOf { polygon ->
        val polygonVertices = polygon.figure.vs
        maxOf(
            polygonVertices.maxOf { it.x } - polygonVertices.minOf { it.x },
            polygonVertices.maxOf { it.y } - polygonVertices.minOf { it.y },
        )
    } / 20
    val viewBox = "${(x0 - strokeWidth).fmt} ${(y0 - strokeWidth).fmt} " +
        "${(w0 + 2 * strokeWidth).fmt} ${(h0 + 2 * strokeWidth).fmt}"
    val markup = """<svg viewBox="$viewBox" stroke="${stroke.toRgbString()}" """ +
        """stroke-width="${strokeWidth.fmt}" stroke-linejoin="round">""" +
        polygons.joinToString("") { polygon ->
            val points = polygon.figure.vs.joinToString(" ") { "${it.x.fmt},${it.y.fmt}" }
            val metadata = buildString {
                polygon.side?.let { append(" data-side=\"").append(it).append('"') }
                polygon.faceKind?.let { append(" data-face-kind=\"").append(it).append('"') }
            }
            """<polygon$metadata fill="${polygon.fill.toRgbString()}" points="$points"></polygon>"""
        } +
        "</svg>"

    Span(attrs = {
        classes(*classes.split(' ').toTypedArray())
        ref { element ->
            element.innerHTML = markup
            onDispose { element.innerHTML = "" }
        }
    })
}
