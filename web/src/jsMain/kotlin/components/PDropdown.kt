/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.components

import androidx.compose.runtime.Composable
import polyhedra.model.util.Tagged
import polyhedra.web.params.EnumParam

@Composable
fun <T : Tagged> PDropdown(param: EnumParam<T>, disabled: Boolean = false) {
    param.observe()
    Dropdown(
        value = param.value,
        options = param.options,
        disabled = disabled,
        onChange = param::updateValue,
    )
}
