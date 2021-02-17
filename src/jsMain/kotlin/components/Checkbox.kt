package polyhedra.js.components

import kotlinx.html.*
import kotlinx.html.js.*
import org.w3c.dom.*
import react.*
import react.dom.*

external interface CheckboxProps : RProps {
    var checked: Boolean
    var onChange: (Boolean) -> Unit
}

fun RBuilder.checkbox(handler: CheckboxProps.() -> Unit) {
    child(Checkbox::class) {
        attrs(handler)
    }
}

class Checkbox : RComponent<CheckboxProps, RState>() {
    override fun RBuilder.render() {
        input(InputType.checkBox) {
            attrs {
                this["checked"] = props.checked
                onChangeFunction = { 
                    props.onChange(!props.checked)
                }
            }
        }
    }
}