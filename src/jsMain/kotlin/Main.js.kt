package polyhedra.js

import react.dom.render
import kotlinx.browser.document
import kotlinx.browser.window
import polyhedra.js.params.*
import polyhedra.js.util.*

private const val historyPushThrottle = 500

fun main() {
//    kotlinext.js.require("./css/style.css")
    window.onload = { onLoad() }
}

private fun onLoad() {
    // Load params
    val rootParams = RootParams()
    val history = createHashHistory()
    var historyPushTimeout = 0
    val str = history.location.pathname.substringAfter('/', "")
    val parsed = ParamParser(str).parse()
    rootParams.loadFrom(parsed)
    rootParams.onUpdate(Param.UpdateType.Value) {
        // throttle updates
        if (historyPushTimeout == 0) {
            historyPushTimeout = window.setTimeout({
                historyPushTimeout = 0
                history.push("/$rootParams")
            }, historyPushThrottle)
        }
    }
    // Unit UI
    render(document.getElementById("root")) {
        child(RootPane::class) {
            attrs {
                param = rootParams
            }
        }
    }
}


