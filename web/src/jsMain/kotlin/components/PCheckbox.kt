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
    ObserveParam(param).value
    org.jetbrains.compose.web.dom.Div(attrs = {
        classes("checkbox")
        if (!disabled) onClick { param.toggle() }
    }) {
        key(param.value, disabled) {
            Input(type = InputType.Checkbox, attrs = {
                if (disabled) disabled()
                checked(param.value)
            })
        }
        Span(attrs = {
            classes("checkmark")
        })
    }
}
