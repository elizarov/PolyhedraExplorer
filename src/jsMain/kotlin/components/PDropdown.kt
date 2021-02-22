package polyhedra.js.components

import polyhedra.js.params.*
import react.*

external interface PDropdownProps<T> : PValueComponentProps<EnumParam<T>> {
    var validateChange: (T) -> Boolean
}

fun <T> RBuilder.pDropdown(handler: PDropdownProps<T>.() -> Unit) {
    child<PDropdownProps<T>, PDropdown<T>> {
        attrs {
            disabled = false
            validateChange = { true }
            handler()
        }
    }
}

class PDropdown<T>(props: PDropdownProps<T>) : PValueComponent<T, EnumParam<T>, PDropdownProps<T>, PValueComponentState<T>>(props) {
    override fun RBuilder.render() {
        dropdown<T> {
            disabled = props.disabled
            value = props.param.value
            options = props.param.options
            onChange = {
                if (props.validateChange(it)) props.param.value = it
            }
        }
    }
}