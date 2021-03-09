package polyhedra.js

import react.dom.render
import kotlinx.browser.document
import kotlinx.browser.window
import polyhedra.js.params.*
import polyhedra.js.poly.*
import polyhedra.js.util.*
import polyhedra.js.worker.*

private const val historyPushThrottle = 500

fun main() {
    if (runWorkerMain()) return
//    kotlinext.js.require("./css/style.css")
    window.onload = { onLoad() }
}

class RootParams : Param.Composite("") {
    val animationParams = using(ViewAnimationParams("a"))
    val render = using(RenderParams("", animationParams))
}

private fun onLoad() {
    val rootParams = loadAndAutoSaveRootParams()
    AnimationTracker(rootParams).start()
    // Unit UI
    render(document.getElementById("root")) {
        child(RootPane::class) {
            attrs {
                param = rootParams
            }
        }
    }
}

private fun loadAndAutoSaveRootParams(): RootParams {
    val rootParams = RootParams()
    val history = createHashHistory()
    var historyPushTimeout = 0
    rootParams.loadFromString(history.location.pathname.substringAfter('/', ""))
    rootParams.onUpdate(Param.UpdateType.TargetValue) {
        // throttle updates
        if (historyPushTimeout == 0) {
            historyPushTimeout = window.setTimeout({
                historyPushTimeout = 0
                history.push("/$rootParams")
            }, historyPushThrottle)
        }
    }
    return rootParams
}


