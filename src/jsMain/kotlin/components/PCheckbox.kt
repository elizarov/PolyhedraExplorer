package polyhedra.js.components

import kotlinx.html.*
import kotlinx.html.js.*
import polyhedra.js.params.*
import react.*
import react.dom.*

fun RBuilder.pCheckbox(handler: PComponentProps<BooleanParam>.() -> Unit) {
    child(PCheckbox::class) {
        attrs {
            disabled = false
            handler()
        }
    }
}

class PCheckbox(props: PComponentProps<BooleanParam>) : PComponent<Boolean, BooleanParam, PComponentProps<BooleanParam>, PComponentState<Boolean>>(props) {
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