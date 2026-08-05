/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.web.dom.Aside
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.I
import org.jetbrains.compose.web.dom.Text
import polyhedra.js.components.ObserveParam
import polyhedra.js.params.Param
import polyhedra.js.poly.FaceContext
import polyhedra.js.poly.PolyCanvas

@Composable
fun RootPane(params: RootParams) {
    ObserveParam(params, Param.TargetValue + Param.Progress).value
    var coreRevision by remember { mutableStateOf(0) }
    val removeCoreListener = remember(params.render.poly) {
        params.render.poly.onCoreResult { coreRevision++ }
    }
    DisposableEffect(removeCoreListener) {
        onDispose(removeCoreListener)
    }
    @Suppress("UNUSED_EXPRESSION")
    coreRevision
    var popup by remember { mutableStateOf<Popup?>(null) }
    var faces by remember { mutableStateOf<FaceContext?>(null) }
    val togglePopup: (Popup?) -> Unit = { requested -> popup = if (popup == requested) null else requested }

    val poly = params.render.poly.poly
    if (poly != null) {
        PolyCanvas(
            classes = "poly",
            params = params.render,
            poly = poly,
            faceContextSink = { faces = it },
            resetPopup = { popup = null },
        )
    } else {
        Div(attrs = { classes("core-status") }) {
            params.render.poly.coreError?.let { Text("Wasm core error: $it") }
                ?: Text("Loading Wasm core… ${params.render.poly.transformProgress}%")
        }
    }
    ControlPane(params.render.poly, popup, togglePopup)
    if (poly != null) PolyInfo(params.render, popup, togglePopup)

    Div(attrs = { classes("btn", "config", *activeWhen(popup, Popup.Config)) }) {
        Button(attrs = {
            classes("square")
            onClick { togglePopup(Popup.Config) }
        }) { I(attrs = { classes("fa", "fa-cog") }) }
    }
    if (popup != Popup.Config && poly != null) {
        Div(attrs = { classes("btn", "export", *activeWhen(popup, Popup.Export)) }) {
            Button(attrs = {
                classes("square")
                onClick { togglePopup(Popup.Export) }
            }) { I(attrs = { classes("fa", "fa-share-square-o") }) }
        }
    }
    when (popup) {
        Popup.Config -> Aside(attrs = { classes("drawer", "config") }) { ConfigPopup(params) }
        Popup.Export -> Aside(attrs = { classes("drawer", "export") }) { ExportPopup(params, faces) }
        else -> Unit
    }
}

internal fun activeWhen(actual: Popup?, expected: Popup): Array<String> =
    if (actual == expected) arrayOf("active") else emptyArray()
