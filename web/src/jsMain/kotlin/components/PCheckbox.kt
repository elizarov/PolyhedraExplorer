/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Span
import polyhedra.js.params.BooleanParam

@Composable
fun PCheckbox(param: BooleanParam, disabled: Boolean = false) {
    param.observe()
    val isChecked = param.value
    org.jetbrains.compose.web.dom.Div(attrs = {
        classes("checkbox")
        if (!disabled) onClick { event ->
            event.preventDefault()
            param.toggle()
        }
    }) {
        key(isChecked, disabled) {
            Input(type = InputType.Checkbox, attrs = {
                if (disabled) disabled()
                checked(isChecked)
            })
        }
        Span(attrs = {
            classes("checkmark")
        })
    }
}
