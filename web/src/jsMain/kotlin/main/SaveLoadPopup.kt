/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.main

import androidx.compose.runtime.*
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLInputElement
import polyhedra.web.poly.CanvasPreviewCapture
import kotlin.js.Date

@Composable
internal fun SaveLoadPopup(
    autoName: String,
    serializeState: () -> String,
    store: SavedConfigurationStore,
    capturePreview: CanvasPreviewCapture,
    onLoad: (String) -> Unit,
    keyboardActions: SaveKeyboardActions? = null,
) {
    var savedConfigurations by remember(store) { mutableStateOf(store.load()) }
    var saveName by remember(autoName) { mutableStateOf(autoName) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Date.now().toLong()) }
    var keyboardSelection by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val interval = window.setInterval({ now = Date.now().toLong() }, 30_000)
        onDispose { window.clearInterval(interval) }
    }

    val saveCurrent: () -> Unit = save@{
        if (saving) return@save
        saving = true
        error = null
        val requestedName = saveName.trim().ifEmpty { autoName }
        val urlState = serializeState()
        capturePreview { preview ->
            if (preview == null) {
                error = "Could not capture the preview. Please try again."
            } else {
                runCatching { store.save(requestedName, urlState, preview) }
                    .onSuccess {
                        savedConfigurations = store.load()
                        saveName = autoName
                        now = Date.now().toLong()
                    }
                    .onFailure {
                        error = it.message ?: "Could not save this configuration."
                    }
            }
            saving = false
        }
    }

    fun navigate(delta: Int): Boolean {
        val rowCount = savedConfigurations.size + 1
        keyboardSelection = (keyboardSelection + delta).mod(rowCount)
        return true
    }

    fun confirm(): Boolean {
        if (keyboardSelection == 0) {
            if (saving) return false
            saveCurrent()
            return true
        }
        val saved = savedConfigurations.getOrNull(keyboardSelection - 1) ?: return false
        onLoad(saved.urlState)
        return true
    }

    SideEffect {
        keyboardActions?.navigate = ::navigate
        keyboardActions?.confirm = ::confirm
    }
    LaunchedEffect(keyboardSelection) {
        document.getElementById(savedConfigurationRowId(keyboardSelection))?.scrollIntoView()
    }

    GroupHeader("Save current")
    Div(attrs = {
        classes("save-current", *(if (keyboardSelection == 0) arrayOf("keyboard-selected") else emptyArray()))
        attr("id", savedConfigurationRowId(0))
        attr("aria-selected", (keyboardSelection == 0).toString())
        onMouseOver { keyboardSelection = 0 }
    }) {
        Input(type = InputType.Text, attrs = {
            classes("save-name")
            attr("aria-label", "Save name")
            attr("placeholder", autoName)
            value(saveName)
            onInput { event -> saveName = event.value }
            onFocus { event -> (event.target as HTMLInputElement).select() }
            onKeyDown { event ->
                if (event.key == "Enter") {
                    event.preventDefault()
                    saveCurrent()
                }
            }
        })
        Button(attrs = {
            classes("save-current-button")
            attr("aria-label", "Save current configuration")
            if (saving) disabled()
            onClick { saveCurrent() }
        }) {
            I(attrs = { classes("fa", if (saving) "fa-spinner" else "fa-floppy-o", *(if (saving) arrayOf("fa-spin") else emptyArray())) })
            Text(if (saving) " Saving…" else " Save")
        }
        error?.let { message ->
            Div(attrs = { classes("save-error") }) { Text(message) }
        }
    }

    GroupHeader("Saved configurations")
    if (savedConfigurations.isEmpty()) {
        Div(attrs = { classes("empty-saves") }) {
            Text("No saves yet. The first save will appear here.")
        }
    } else {
        Div(attrs = { classes("saved-configurations") }) {
            for ((index, saved) in savedConfigurations.withIndex()) {
                val rowIndex = index + 1
                Button(attrs = {
                    classes(
                        "saved-configuration",
                        *(if (keyboardSelection == rowIndex) arrayOf("keyboard-selected") else emptyArray()),
                    )
                    attr("id", savedConfigurationRowId(rowIndex))
                    attr("aria-selected", (keyboardSelection == rowIndex).toString())
                    attr("aria-label", "Load ${saved.name}, saved ${relativeSavedTime(saved.savedAtEpochMillis, now)}")
                    attr("title", "Saved ${Date(saved.savedAtEpochMillis.toDouble()).toLocaleString()}")
                    onMouseOver { keyboardSelection = rowIndex }
                    onClick { onLoad(saved.urlState) }
                }) {
                    Img(
                        src = saved.previewDataUrl,
                        alt = "Preview of ${saved.name}",
                        attrs = { classes("saved-preview") },
                    )
                    Span(attrs = { classes("saved-details") }) {
                        Span(attrs = { classes("saved-name") }) { Text(saved.name) }
                        Span(attrs = { classes("saved-time") }) {
                            Text(relativeSavedTime(saved.savedAtEpochMillis, now))
                        }
                    }
                    I(attrs = { classes("fa", "fa-chevron-right", "saved-load-icon") })
                }
            }
        }
    }
}

private fun savedConfigurationRowId(index: Int): String = "saved-configuration-row-$index"
