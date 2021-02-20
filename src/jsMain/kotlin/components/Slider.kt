package polyhedra.js.components

import kotlinx.html.*
import kotlinx.html.js.*
import org.w3c.dom.*
import react.*
import react.dom.*
import kotlin.math.*

external interface SliderProps : RProps {
    var disabled: Boolean
    var min: Double
    var max: Double
    var step: Double
    var value: Double
    var onChange: (Double) -> Unit
}

fun RBuilder.slider(handler: SliderProps.() -> Unit) {
    child(Slider::class) {
        attrs {
            disabled = false
            handler()
        }
    }
}

class Slider : RComponent<SliderProps, RState>() {
    private fun Double.intStr() = roundToInt().toString()

    override fun RBuilder.render() {
        input(InputType.range) {
            attrs {
                disabled = props.disabled
                min = (props.min / props.step).intStr()
                max = (props.max / props.step).intStr()
                value = (props.value / props.step).intStr()
                onChangeFunction = { event ->
                    val valueString = (event.target as HTMLInputElement).value
                    props.onChange(valueString.toInt() * props.step)
                }
            }
        }
    }
}