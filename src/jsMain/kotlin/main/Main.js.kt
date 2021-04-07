/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.main

import kotlinx.browser.*
import polyhedra.js.params.*
import polyhedra.js.poly.*
import polyhedra.js.util.*
import polyhedra.js.worker.*
import react.dom.*

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
    val path = decodeURI(history.location.pathname)
    rootParams.loadFromString(path.substringAfter('/', ""))
    rootParams.onNotifyUpdated(Param.TargetValue) {
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

external fun decodeURI(s: String): String

