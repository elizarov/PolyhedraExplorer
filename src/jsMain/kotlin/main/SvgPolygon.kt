/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.main

import kotlinx.html.*
import polyhedra.common.poly.*
import polyhedra.common.util.*
import polyhedra.js.util.*
import react.*
import react.dom.*

fun RBuilder.svgPolygon(classes: String, figure: PolygonProjection, stroke: Color, fill: Color) {
    val x0 = figure.vs.minOf { it.x }
    val y0 = figure.vs.minOf { it.y }
    val w0 = figure.vs.maxOf { it.x } - x0
    val h0 = figure.vs.maxOf { it.y } - y0
    val sw = maxOf(w0, h0) / 20
    svg(
        classes = classes,
        viewBox = "${(x0 - sw).fmt} ${(y0 - sw).fmt} ${(w0 + 2 * sw).fmt} ${(h0 + 2 * sw).fmt}",
        stroke = stroke.toRgbString(),
        strokeWidth = sw.fmt,
        fill = fill.toRgbString()
    ) {
        polygon(figure.vs.joinToString(" ") { "${it.x.fmt},${it.y.fmt}" })
    }
}

inline fun RBuilder.svg(
    classes: String,
    viewBox: String,
    stroke: String,
    strokeWidth: String,
    fill: String,
    block: RDOMBuilder<SVG>.() -> Unit
): ReactElement =
    tag(block) { SVG(
        attributesMapOf(
            "class", classes,
            "viewBox", viewBox,
            "stroke", stroke,
            "strokeWidth", strokeWidth,
            "fill", fill
        ), it)
    }

open class POLYGON(initialAttributes : Map<String, String>, override val consumer : TagConsumer<*>) :
    HTMLTag("polygon", consumer, initialAttributes, "http://www.w3.org/2000/svg", false, true)

fun RDOMBuilder<SVG>.polygon(points: String): ReactElement =
    child(RDOMBuilder { POLYGON(attributesMapOf("points", points), it) }.create())
