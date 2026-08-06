/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.main

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Span
import polyhedra.model.poly.PolygonProjection
import polyhedra.model.util.fmt
import polyhedra.web.util.Color
import polyhedra.web.util.toRgbString

@Composable
fun SvgPolygon(classes: String, figure: PolygonProjection, stroke: Color, fill: Color) {
    val x0 = figure.vs.minOf { it.x }
    val y0 = figure.vs.minOf { it.y }
    val w0 = figure.vs.maxOf { it.x } - x0
    val h0 = figure.vs.maxOf { it.y } - y0
    val strokeWidth = maxOf(w0, h0) / 20
    val viewBox = "${(x0 - strokeWidth).fmt} ${(y0 - strokeWidth).fmt} " +
        "${(w0 + 2 * strokeWidth).fmt} ${(h0 + 2 * strokeWidth).fmt}"
    val points = figure.vs.joinToString(" ") { "${it.x.fmt},${it.y.fmt}" }
    val markup = """<svg viewBox="$viewBox" stroke="${stroke.toRgbString()}" """ +
        """stroke-width="${strokeWidth.fmt}" fill="${fill.toRgbString()}">""" +
        """<polygon points="$points"></polygon></svg>"""

    Span(attrs = {
        classes(*classes.split(' ').toTypedArray())
        ref { element ->
            element.innerHTML = markup
            onDispose { element.innerHTML = "" }
        }
    })
}
