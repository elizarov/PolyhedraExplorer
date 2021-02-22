package polyhedra.js.components

import polyhedra.common.*
import polyhedra.js.params.*
import react.*

fun <T : Tagged> RBuilder.pDropdown(handler: PValueComponentProps<EnumParam<T>>.() -> Unit) {
    child<PValueComponentProps<EnumParam<T>>, PDropdown<T>> {
        attrs {
            disabled = false
            handler()
        }
    }
}

class PDropdown<T : Tagged>(props: PValueComponentProps<EnumParam<T>>) : PValueComponent<T, EnumParam<T>, PValueComponentProps<EnumParam<T>>, PValueComponentState<T>>(props) {
    override fun RBuilder.render() {
        dropdown<T> {
            disabled = props.disabled
            value = props.param.value
            options = props.param.options
            onChange = { props.param.value = it }
        }
    }
}