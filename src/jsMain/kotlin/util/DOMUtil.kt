package polyhedra.js.util

import kotlinx.browser.*
import org.w3c.dom.*

fun HTMLElement.computedStyle() =
    window.document.defaultView!!.getComputedStyle(this)