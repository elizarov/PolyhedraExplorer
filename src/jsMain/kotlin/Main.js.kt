package polyhedra.js

import react.dom.render
import kotlinx.browser.document
import kotlinx.browser.window

fun main() {
//    kotlinext.js.require("./css/style.css")
    window.onload = {
        render(document.getElementById("root")) {
            child(RootPane::class) {}
        }
    }
}
