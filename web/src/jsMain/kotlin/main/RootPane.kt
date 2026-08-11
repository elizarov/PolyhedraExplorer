/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Aside
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.I
import org.jetbrains.compose.web.dom.Text
import polyhedra.model.poly.Polyhedron
import polyhedra.web.components.observe
import polyhedra.web.params.Param
import polyhedra.web.poly.CanvasPreviewCapture
import polyhedra.web.poly.FaceContext
import polyhedra.web.poly.PolyCanvas
import polyhedra.web.poly.PolyParams

internal const val PROJECT_GITHUB_URL = "https://github.com/elizarov/PolyhedraExplorer"

@Composable
fun RootPane(params: RootParams) {
    params.render.poly.observe(Param.TargetValue + Param.Progress)
    var popup by remember { mutableStateOf<Popup?>(null) }
    var faces by remember { mutableStateOf<FaceContext?>(null) }
    var fps by remember { mutableStateOf<Int?>(null) }
    var capturePreview by remember { mutableStateOf<CanvasPreviewCapture>({ onCaptured -> onCaptured(null) }) }
    val savedConfigurationStore = remember { SavedConfigurationStore() }
    val controlKeyboardActions = remember { ControlKeyboardActions() }
    val saveKeyboardActions = remember { SaveKeyboardActions() }
    val togglePopup: (Popup?) -> Unit = { requested ->
        params.render.poly.clearRolloverSelection()
        popup = if (popup == requested) null else requested
    }

    val poly = params.render.poly.poly
    KeyboardShortcutListener { command ->
        fun togglePolyPopup(requested: Popup): Boolean {
            if (poly == null) return false
            togglePopup(requested)
            return true
        }

        when (command) {
            KeyboardCommand.PreviousItem -> controlKeyboardActions.adjustHorizontal(-1)
            KeyboardCommand.NextItem -> controlKeyboardActions.adjustHorizontal(1)
            KeyboardCommand.PreviousDetail -> if (popup == Popup.AddTransform) {
                controlKeyboardActions.navigateAddTransform(-1)
            } else if (popup == Popup.Faces || popup == Popup.Edges || popup == Popup.Vertices) {
                poly?.let { navigateInspectionOrbit(popup, it, params.render.poly, -1) } ?: false
            } else if (popup == Popup.Saves) {
                saveKeyboardActions.navigate(-1)
            } else {
                controlKeyboardActions.adjustVertical(-1)
            }
            KeyboardCommand.NextDetail -> if (popup == Popup.AddTransform) {
                controlKeyboardActions.navigateAddTransform(1)
            } else if (popup == Popup.Faces || popup == Popup.Edges || popup == Popup.Vertices) {
                poly?.let { navigateInspectionOrbit(popup, it, params.render.poly, 1) } ?: false
            } else if (popup == Popup.Saves) {
                saveKeyboardActions.navigate(1)
            } else {
                controlKeyboardActions.adjustVertical(1)
            }
            KeyboardCommand.ConfirmSelection -> when (popup) {
                Popup.AddTransform -> controlKeyboardActions.confirmAddTransform()
                Popup.Saves -> saveKeyboardActions.confirm()
                null -> controlKeyboardActions.acceptSuggestion()
                else -> false
            }
            KeyboardCommand.AddTransform -> controlKeyboardActions.addTransform()
            KeyboardCommand.DeleteTransform -> controlKeyboardActions.deleteTransform()
            KeyboardCommand.ClosePopups -> if (popup == null) {
                false
            } else {
                togglePopup(null)
                true
            }
            KeyboardCommand.ToggleFaceVisibility -> {
                val faceKinds = poly?.faceKinds?.keys ?: return@KeyboardShortcutListener false
                val hiddenFaces = params.render.poly.hideFaces.value
                val selectedFace = params.render.poly.selectedFace.value
                val updated = faceVisibilityAfterSpace(
                    hiddenFaces,
                    faceKinds,
                    selectedFace,
                    individual = popup == Popup.Faces,
                ) ?: return@KeyboardShortcutListener false
                params.render.poly.hideFaces.updateValue(updated)
                true
            }
            KeyboardCommand.ToggleFacesPopup -> togglePolyPopup(Popup.Faces)
            KeyboardCommand.ToggleEdgesPopup -> togglePolyPopup(Popup.Edges)
            KeyboardCommand.ToggleVerticesPopup -> togglePolyPopup(Popup.Vertices)
            KeyboardCommand.ToggleSymmetry -> {
                if (poly == null) false else {
                    val showSymmetry = params.render.poly.showSymmetry
                    showSymmetry.updateValue(!showSymmetry.value)
                    true
                }
            }
            KeyboardCommand.ToggleRotation -> {
                val rotation = params.animationParams.animatedRotation
                rotation.updateValue(!rotation.value)
                true
            }
            KeyboardCommand.ToggleConfig -> {
                togglePopup(Popup.Config)
                true
            }
            KeyboardCommand.ToggleExport -> {
                if (poly == null) false else {
                    val exportOpen = popup == Popup.Export || popup == Popup.PrintColor
                    togglePopup(if (exportOpen) null else Popup.Export)
                    true
                }
            }
            KeyboardCommand.ToggleSaves -> togglePolyPopup(Popup.Saves)
            KeyboardCommand.ToggleHelp -> {
                togglePopup(Popup.Help)
                true
            }
        }
    }
    if (poly != null) {
        PolyCanvas(
            classes = "poly",
            params = params.render,
            popup = popup,
            faceContextSink = { faces = it },
            previewCaptureSink = { capturePreview = it },
            fpsSink = { fps = it },
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
    ControlPane(params.render.poly, popup, togglePopup, controlKeyboardActions)
    if (poly != null) PolyInfo(params.render, popup, togglePopup)

    Div(attrs = { classes("btn", "config", *activeWhen(popup, Popup.Config)) }) {
        Button(attrs = {
            classes("square")
            onClick { togglePopup(Popup.Config) }
        }) { I(attrs = { classes("fa", "fa-cog") }) }
    }
    if (popup != Popup.Config && poly != null) {
        val exportOpen = popup == Popup.Export || popup == Popup.PrintColor
        Div(attrs = { classes("btn", "export", *(if (exportOpen) arrayOf("active") else emptyArray())) }) {
            Button(attrs = {
                classes("square")
                onClick { togglePopup(if (exportOpen) null else Popup.Export) }
            }) { I(attrs = { classes("fa", "fa-share-square-o") }) }
        }
        Div(attrs = { classes("btn", "saves", *activeWhen(popup, Popup.Saves)) }) {
            Button(attrs = {
                classes("square")
                attr("aria-label", "Saved configurations")
                onClick { togglePopup(Popup.Saves) }
            }) {
                I(attrs = { classes("fa", "fa-floppy-o") })
                Aside(attrs = { classes("tooltip-text") }) { Text("Saved configurations") }
            }
        }
        HelpButton(popup == Popup.Help) { togglePopup(Popup.Help) }
    }
    GitHubCorner(fps)
    when (popup) {
        Popup.Config -> Aside(attrs = { classes("drawer", "config") }) { ConfigPopup(params) }
        Popup.Export -> Aside(attrs = { classes("drawer", "export") }) {
            ExportPopup(params, faces, onPickColor = { popup = Popup.PrintColor })
        }
        Popup.PrintColor -> Aside(attrs = { classes("drawer", "export", "print-color-picker") }) {
            PrintColorPopup(params.render.printPreview, onBack = { popup = Popup.Export })
        }
        Popup.Saves -> Aside(attrs = { classes("drawer", "saves") }) {
            SaveLoadPopup(
                autoName = params.render.poly.polyName,
                serializeState = params::toString,
                store = savedConfigurationStore,
                capturePreview = capturePreview,
                onLoad = ::loadSavedConfiguration,
                keyboardActions = saveKeyboardActions,
            )
        }
        Popup.Help -> Aside(attrs = { classes("drawer", "help") }) { KeyboardHelpPopup() }
        else -> Unit
    }
}

internal fun <K> adjacentOrbit(kinds: Collection<K>, selected: K?, delta: Int): K? {
    if (kinds.isEmpty()) return null
    val ordered = kinds.toList()
    val currentIndex = ordered.indexOf(selected)
    val nextIndex = if (currentIndex < 0) {
        if (delta < 0) ordered.lastIndex else 0
    } else {
        (currentIndex + delta).mod(ordered.size)
    }
    return ordered[nextIndex]
}

internal fun navigateInspectionOrbit(
    popup: Popup?,
    poly: Polyhedron,
    params: PolyParams,
    delta: Int,
): Boolean = when (popup) {
    Popup.Faces -> adjacentOrbit(poly.faceKinds.keys, params.selectedFace.value, delta)?.let {
        params.selectedFace.updateValue(it)
        scrollSelectedInspectionRowIntoView()
        true
    } ?: false
    Popup.Edges -> adjacentOrbit(poly.edgeKinds.keys, params.selectedEdge.value, delta)?.let {
        params.selectedEdge.updateValue(it)
        scrollSelectedInspectionRowIntoView()
        true
    } ?: false
    Popup.Vertices -> adjacentOrbit(poly.vertexKinds.keys, params.selectedVertex.value, delta)?.let {
        params.selectedVertex.updateValue(it)
        scrollSelectedInspectionRowIntoView()
        true
    } ?: false
    else -> false
}

private fun scrollSelectedInspectionRowIntoView() {
    window.requestAnimationFrame {
        document.querySelector("aside.fev tr.info.selected")?.scrollIntoView()
    }
}

internal fun <K> faceVisibilityAfterSpace(
    hiddenFaces: Set<K>,
    faceKinds: Set<K>,
    selectedFace: K?,
    individual: Boolean,
): Set<K>? = if (individual) {
    selectedFace?.let { selected ->
        if (selected in hiddenFaces) hiddenFaces - selected else hiddenFaces + selected
    }
} else {
    if (hiddenFaces.isEmpty()) faceKinds else emptySet()
}

@Composable
internal fun HelpButton(active: Boolean, onClick: () -> Unit) {
    Div(attrs = {
        classes("btn", "help", *(if (active) arrayOf("active") else emptyArray()))
    }) {
        Button(attrs = {
            classes("square")
            attr("aria-label", "Keyboard help")
            onClick { onClick() }
        }) {
            Text("?")
            Aside(attrs = { classes("tooltip-text") }) { Text("Keyboard help") }
        }
    }
}

@Composable
internal fun GitHubCorner(fps: Int?) {
    Div(attrs = { classes("github-corner") }) {
        A(href = PROJECT_GITHUB_URL, attrs = {
            classes("github-link")
            attr("target", "_blank")
            attr("rel", "noopener noreferrer")
            attr("aria-label", "Open Polyhedra Explorer on GitHub")
        }) {
            I(attrs = { classes("fa", "fa-github") })
        }
        Div(attrs = { classes("github-caption", "fps") }) {
            Text(fpsCaption(fps))
        }
    }
}

internal fun fpsCaption(fps: Int?): String = fps?.let { "$it fps" } ?: "Open Source"

internal fun activeWhen(actual: Popup?, expected: Popup): Array<String> =
    if (actual == expected) arrayOf("active") else emptyArray()
