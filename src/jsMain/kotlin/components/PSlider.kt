package polyhedra.js.components

import kotlinx.html.*
import kotlinx.html.js.*
import org.w3c.dom.*
import polyhedra.js.params.*
import react.*
import react.dom.*
import kotlin.math.*

fun RBuilder.pSlider(param: DoubleParam, disabled: Boolean = false) {
    child(PSlider::class) {
        attrs {
            this.param = param
            this.disabled = disabled
        }
    }
}

class PSlider(props: PValueComponentProps<DoubleParam>) : PValueComponent<Double, DoubleParam, PValueComponentProps<DoubleParam>, PValueComponentState<Double>>(props) {
    private fun Double.intStr() = roundToInt().toString()

    override fun RBuilder.render() {
        input(InputType.range) {
            attrs {
                disabled = props.disabled
                with(props) {
                    min = (param.min / param.step).intStr()
                    max = (param.max / param.step).intStr()
                    value = (state.value / param.step).intStr()
                }
                onChangeFunction = { event ->
                    props.param.value = (event.target as HTMLInputElement).value.toInt() * props.param.step
                }
            }
        }
    }
}