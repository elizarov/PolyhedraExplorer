/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.components

import androidx.compose.runtime.Composable
import polyhedra.common.util.Tagged
import polyhedra.js.params.EnumParam

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
