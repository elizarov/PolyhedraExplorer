/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text

@Composable
fun <T> Dropdown(
    value: T,
    options: Collection<T>,
    disabled: Boolean = false,
    onChange: (T) -> Unit,
) {
    Div(attrs = { classes("select") }) {
        Select(attrs = {
            if (disabled) disabled()
            onChange { event ->
                val selectedValue = event.value ?: return@onChange
                onChange(options.first { it.toString() == selectedValue })
            }
        }) {
            for (option in options) {
                Option(
                    value = option.toString(),
                    attrs = { if (option == value) selected() },
                ) { Text(option.toString()) }
            }
        }
    }
}
