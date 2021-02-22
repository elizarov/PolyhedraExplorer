package polyhedra.js.components

import kotlinx.html.*
import kotlinx.html.js.*
import polyhedra.js.params.*
import react.*
import react.dom.*

fun RBuilder.pCheckbox(param: BooleanParam, disabled: Boolean = false) {
    child(PCheckbox::class) {
        attrs {
            this.param = param
            this.disabled = disabled
        }
    }
}

class PCheckbox(props: PValueComponentProps<BooleanParam>) : PValueComponent<Boolean, BooleanParam, PValueComponentProps<BooleanParam>, PValueComponentState<Boolean>>(props) {
    override fun RBuilder.render() {
        input(InputType.checkBox) {
            attrs {
                disabled = props.disabled
                // See https://github.com/JetBrains/kotlin-wrappers/issues/35
                this["checked"] = state.value
                onChangeFunction = { 
                    props.param.toggle()
                }
            }
        }
    }
}