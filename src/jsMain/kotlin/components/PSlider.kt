/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.components

import kotlinx.html.*
import kotlinx.html.js.*
import org.w3c.dom.*
import polyhedra.common.util.*
import polyhedra.js.params.*
import react.*
import react.dom.*
import kotlin.math.*

external interface PSliderProps : PValueComponentProps<DoubleParam> {
    var showValue: Boolean
}

fun RBuilder.pSlider(param: DoubleParam, disabled: Boolean = false, showValue: Boolean = true) {
    child(PSlider::class) {
        attrs {
            this.params = param
            this.disabled = disabled
            this.showValue = showValue
        }
    }
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class PSlider(props: PSliderProps) : PValueComponent<Double, DoubleParam, PSliderProps, PValueComponentState<Double>>(props) {
    private fun Double.intStr() = roundToInt().toString()

    override fun RBuilder.render() {
        input(InputType.range) {
            attrs {
                disabled = props.disabled
                with(props) {
                    min = (params.min / params.step).intStr()
                    max = (params.max / params.step).intStr()
                    value = (state.value / params.step).intStr()
                }
                onChangeFunction = { event ->
                    props.params.updateValue((event.target as HTMLInputElement).value.toInt() * props.params.step)
                }
            }
        }
        if (props.showValue) {
            span { +state.value.fmt }
        }
    }
}