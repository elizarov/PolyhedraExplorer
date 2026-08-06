/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.main

import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import polyhedra.web.params.AnimationTracker
import polyhedra.web.params.Param
import polyhedra.web.params.loadFromString
import polyhedra.web.poly.ExportParams
import polyhedra.web.poly.RenderParams
import polyhedra.web.poly.ViewAnimationParams

private const val historyPushThrottle = 500

fun main() {
    window.onload = {
        val rootParams = loadAndAutoSaveRootParams()
        AnimationTracker(rootParams).start()
        renderComposable(rootElementId = "root") {
            RootPane(rootParams)
        }
        rootParams.render.poly.startCore()
    }
}

class RootParams : Param.Composite("") {
    val animationParams = using(ViewAnimationParams("a"))
    val render = using(RenderParams("", animationParams))
    val export = using(ExportParams("e"))
}

private fun loadAndAutoSaveRootParams(): RootParams {
    val rootParams = RootParams()
    var historyPushTimeout = 0
    val path = decodeURIComponent(window.location.hash.removePrefix("#").substringAfter('/', ""))
    rootParams.loadFromString(path)
    rootParams.onNotifyUpdated(Param.TargetValue) {
        if (historyPushTimeout == 0) {
            historyPushTimeout = window.setTimeout({
                historyPushTimeout = 0
                window.location.hash = "/$rootParams"
            }, historyPushThrottle)
        }
    }
    return rootParams
}

external fun decodeURIComponent(value: String): String
