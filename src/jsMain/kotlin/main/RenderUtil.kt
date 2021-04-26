/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.main

import kotlinx.html.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

fun <T> RBuilder.messageSpan(msg: IndicatorMessage<T>) {
    span(msg.indicator.classes) { +msg.indicator.text }
    aside("tooltip-text") {
        +msg.indicator.tooltip.replace("{}", msg.value.toString())
    }
}

fun RBuilder.groupHeader(text: String) {
    div("text-row") {
        div("header") { +text }
    }
}

fun RBuilder.tableBody(block: RDOMBuilder<TBODY>.() -> Unit) {
    table {
        tbody(block = block)
    }
}

fun RDOMBuilder<TBODY>.controlRow(label: String, block: RDOMBuilder<TD>.() -> Unit) {
    tr("control") {
        td { +label }
        td(block = block)
    }
}

fun RDOMBuilder<TBODY>.controlRow2(
    label: String,
    block1: RDOMBuilder<TD>.() -> Unit,
    block2: RDOMBuilder<TD>.() -> Unit
) {
    tr("control") {
        td { +label }
        td(block = block1)
        td(block = block2)
    }
}