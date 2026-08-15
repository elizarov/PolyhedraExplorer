/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Input
import polyhedra.web.params.DoubleParam
import kotlin.math.roundToInt

@Composable
fun PSlider(
    param: DoubleParam,
    disabled: Boolean = false,
    valueScale: Double = 1.0,
    valuePrecision: Int = 6,
    unit: String? = null,
    snapInputToStep: Boolean = true,
    ariaLabel: String = "Slider value",
) {
    require(valueScale > 0.0 && valueScale.isFinite())
    param.observe()
    fun Double.intString() = roundToInt().toString()
    val targetValue = param.targetValue

    Input(type = InputType.Range, attrs = {
        if (disabled) disabled()
        attr("min", (param.min / param.step).intString())
        attr("max", (param.max / param.step).intString())
        value((targetValue / param.step).intString())
        onInput { event ->
            event.value?.let { param.updateValue(it.toDouble() * param.step) }
        }
    })
    NumericValueInput(
        value = targetValue * valueScale,
        min = param.min * valueScale,
        max = param.max * valueScale,
        step = param.step * valueScale,
        ariaLabel = ariaLabel,
        disabled = disabled,
        precision = valuePrecision,
        unit = unit,
        snapToStep = snapInputToStep,
    ) { displayedValue ->
        if (snapInputToStep) {
            param.updateValue(displayedValue / valueScale)
        } else {
            param.updateUnsnappedValue(displayedValue / valueScale)
        }
        param.targetValue * valueScale
    }
}
