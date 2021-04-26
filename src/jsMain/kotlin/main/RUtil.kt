package polyhedra.js.main

import kotlinx.html.*
import kotlinx.html.js.*
import org.w3c.dom.events.*
import react.dom.*

fun RDOMBuilder<CommonAttributeGroupFacade>.onClick(handler: (Event) -> Unit) {
    attrs {
        onClickFunction = handler
    }
}