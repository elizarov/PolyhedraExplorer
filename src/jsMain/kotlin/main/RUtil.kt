package polyhedra.js.main

import kotlinx.html.*
import react.*
import react.dom.*

fun RBuilder.popupHeader(text: String) {
    div("header-container") {
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