/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.web.dom.Aside
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.I
import org.jetbrains.compose.web.dom.Text
import polyhedra.web.components.observe
import polyhedra.web.params.Param
import polyhedra.web.poly.FaceContext
import polyhedra.web.poly.PolyCanvas

@Composable
fun RootPane(params: RootParams) {
    params.render.poly.observe(Param.TargetValue + Param.Progress)
    var popup by remember { mutableStateOf<Popup?>(null) }
    var faces by remember { mutableStateOf<FaceContext?>(null) }
    val togglePopup: (Popup?) -> Unit = { requested ->
        params.render.poly.clearRolloverSelection()
        popup = if (popup == requested) null else requested
    }

    val poly = params.render.poly.poly
    if (poly != null) {
        PolyCanvas(
            classes = "poly",
            params = params.render,
            popup = popup,
            faceContextSink = { faces = it },
            resetPopup = {
                params.render.poly.clearRolloverSelection()
                popup = null
            },
        )
    } else if (!params.render.poly.coreLoaded || params.render.poly.coreError != null) {
        Div(attrs = { classes("core-status") }) {
            params.render.poly.coreError?.let { Text("Wasm core error: $it") }
                ?: Text("Loading Wasm core…")
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
