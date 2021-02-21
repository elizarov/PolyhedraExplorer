package polyhedra.js.components

import polyhedra.js.params.*
import react.*

fun <T> RBuilder.pDropdown(handler: PComponentProps<EnumParam<T>>.() -> Unit) {
    child<PComponentProps<EnumParam<T>>, PDropdown<T>> {
        attrs {
            disabled = false
            handler()
        }
    }
}

class PDropdown<T>(props: PComponentProps<EnumParam<T>>) : PComponent<T, EnumParam<T>, PComponentProps<EnumParam<T>>, PComponentState<T>>(props) {
    override fun RBuilder.render() {
        dropdown<T> {
            disabled = props.disabled
            value = props.param.value
            options = props.param.options
            onChange = { props.param.value = it }
        }
    }
}