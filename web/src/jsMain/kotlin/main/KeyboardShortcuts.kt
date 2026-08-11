/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Tr
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.KeyboardEvent

internal enum class KeyboardShortcutSection(val title: String) {
    Navigation("Navigate and edit"),
    View("Explore the view"),
    Panels("Open panels"),
}

internal enum class KeyboardCommand(
    val displayedKey: String,
    val description: String,
    val section: KeyboardShortcutSection,
) {
    PreviousItem("←", "Move left", KeyboardShortcutSection.Navigation),
    NextItem("→", "Move right", KeyboardShortcutSection.Navigation),
    PreviousDetail("↑", "Move up", KeyboardShortcutSection.Navigation),
    NextDetail("↓", "Move down", KeyboardShortcutSection.Navigation),
    ConfirmSelection("Enter", "Select or replace", KeyboardShortcutSection.Navigation),
    AddTransform("+", "Add a transform", KeyboardShortcutSection.Navigation),
    DeleteTransform("Del / ⌫", "Delete or reset", KeyboardShortcutSection.Navigation),
    ClosePopups("Esc", "Close the open popup", KeyboardShortcutSection.Navigation),
    ToggleFaceVisibility("Space", "Toggle face visibility", KeyboardShortcutSection.View),
    ToggleFacesPopup("F", "Toggle the faces popup", KeyboardShortcutSection.View),
    ToggleEdgesPopup("E", "Toggle the edges popup", KeyboardShortcutSection.View),
    ToggleVerticesPopup("V", "Toggle the vertices popup", KeyboardShortcutSection.View),
    ToggleSymmetry("Y", "Show or hide symmetry geometry", KeyboardShortcutSection.View),
    ToggleRotation("R", "Start or stop automatic rotation", KeyboardShortcutSection.View),
    ToggleConfig("C", "Toggle configuration", KeyboardShortcutSection.Panels),
    ToggleExport("X", "Toggle export", KeyboardShortcutSection.Panels),
    ToggleSaves("S", "Toggle saved configurations", KeyboardShortcutSection.Panels),
    ToggleHelp("?", "Toggle this keyboard help", KeyboardShortcutSection.Panels),
}

internal class ControlKeyboardActions {
    var adjustHorizontal: (Int) -> Boolean = { false }
    var adjustVertical: (Int) -> Boolean = { false }
    var navigateAddTransform: (Int) -> Boolean = { false }
    var confirmAddTransform: () -> Boolean = { false }
    var acceptSuggestion: () -> Boolean = { false }
    var addTransform: () -> Boolean = { false }
    var deleteTransform: () -> Boolean = { false }
}

internal class SaveKeyboardActions {
    var navigate: (Int) -> Boolean = { false }
    var confirm: () -> Boolean = { false }
}

internal fun keyboardCommandFor(
    key: String,
    altKey: Boolean = false,
    ctrlKey: Boolean = false,
    metaKey: Boolean = false,
): KeyboardCommand? {
    if (altKey || ctrlKey || metaKey) return null
    return when (key) {
        "ArrowLeft" -> KeyboardCommand.PreviousItem
        "ArrowRight" -> KeyboardCommand.NextItem
        "ArrowUp" -> KeyboardCommand.PreviousDetail
        "ArrowDown" -> KeyboardCommand.NextDetail
        "Enter" -> KeyboardCommand.ConfirmSelection
        "+", "Add" -> KeyboardCommand.AddTransform
        "Delete", "Backspace" -> KeyboardCommand.DeleteTransform
        "Escape", "Esc" -> KeyboardCommand.ClosePopups
        " ", "Spacebar" -> KeyboardCommand.ToggleFaceVisibility
        "?" -> KeyboardCommand.ToggleHelp
        else -> when (key.lowercase()) {
            "f" -> KeyboardCommand.ToggleFacesPopup
            "e" -> KeyboardCommand.ToggleEdgesPopup
            "v" -> KeyboardCommand.ToggleVerticesPopup
            "y" -> KeyboardCommand.ToggleSymmetry
            "r" -> KeyboardCommand.ToggleRotation
            "c" -> KeyboardCommand.ToggleConfig
            "x" -> KeyboardCommand.ToggleExport
            "s" -> KeyboardCommand.ToggleSaves
            else -> null
        }
    }
}

internal fun isKeyboardInputTarget(target: Any?): Boolean {
    val element = target as? HTMLElement ?: return false
    return element is HTMLInputElement ||
        element is HTMLTextAreaElement ||
        element is HTMLSelectElement ||
        element.isContentEditable
}

@Composable
internal fun KeyboardShortcutListener(onCommand: (KeyboardCommand) -> Boolean) {
    val currentOnCommand by rememberUpdatedState(onCommand)
    DisposableEffect(Unit) {
        val listener = EventListener { rawEvent ->
            val event = rawEvent as? KeyboardEvent ?: return@EventListener
            if (event.defaultPrevented) return@EventListener
            val command = keyboardCommandFor(event.key, event.altKey, event.ctrlKey, event.metaKey)
                ?: return@EventListener
            if (command != KeyboardCommand.ClosePopups && isKeyboardInputTarget(event.target)) {
                return@EventListener
            }
            if (currentOnCommand(command)) event.preventDefault()
        }
        document.addEventListener("keydown", listener)
        onDispose { document.removeEventListener("keydown", listener) }
    }
}

@Composable
internal fun KeyboardHelpPopup(applicationVersion: String = APPLICATION_VERSION) {
    Div(attrs = { classes("keyboard-help-about") }) {
        Span(attrs = { classes("keyboard-help-title") }) { Text("Polyhedra Explorer") }
        Span(attrs = { classes("keyboard-help-version") }) { Text("Version $applicationVersion") }
    }
    for (section in KeyboardShortcutSection.entries) {
        GroupHeader(section.title)
        Table(attrs = { classes("keyboard-help-table") }) {
            Tbody {
                for (command in KeyboardCommand.entries.filter { it.section == section }) {
                    Tr {
                        Td(attrs = { classes("keyboard-help-key") }) {
                            Span(attrs = { classes("keyboard-help-keycap") }) {
                                Text(command.displayedKey)
                            }
                        }
                        Td { Text(command.description) }
                    }
                }
            }
        }
    }
}
