package polyhedra.js.components

import kotlinx.html.js.*
import org.w3c.dom.*
import react.*
import react.dom.*

external interface DropdownProps<T> : RProps {
    var value: T
    var options: List<T>
    var onChange: (T) -> Unit
}

fun <T> RBuilder.dropdown(handler: DropdownProps<T>.() -> Unit) {
    child<DropdownProps<T>, Dropdown<T>> {
        attrs(handler)
    }
}

class Dropdown<T>(props: DropdownProps<T>) : RComponent<DropdownProps<T>, RState>(props) {
    override fun RBuilder.render() {
        select {
            attrs {
                this["value"] = props.value.toString()
                onChangeFunction = { event ->
                    val valueString = (event.target as HTMLSelectElement).value
                    val value = props.options.first { it.toString() == valueString }
                    props.onChange(value)
                }
            }
            for (opt in props.options) {
                option {
                    attrs {
                        value = opt.toString()
                    }
                    +opt.toString()
                }
            }
        }
    }
}