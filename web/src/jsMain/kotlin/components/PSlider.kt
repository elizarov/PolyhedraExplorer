/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import polyhedra.common.util.fmt
import polyhedra.js.params.DoubleParam
import kotlin.math.roundToInt

@Composable
fun PSlider(param: DoubleParam, disabled: Boolean = false, showValue: Boolean = true) {
    ObserveParam(param).value
    fun Double.intString() = roundToInt().toString()

    Input(type = InputType.Range, attrs = {
        if (disabled) disabled()
        attr("min", (param.min / param.step).intString())
        attr("max", (param.max / param.step).intString())
        value((param.value / param.step).intString())
        onInput { event ->
            event.value?.let { param.updateValue(it.toDouble() * param.step) }
        }
    })
    if (showValue) Span { Text(param.value.fmt) }
}
