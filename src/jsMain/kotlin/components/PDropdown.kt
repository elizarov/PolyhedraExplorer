package polyhedra.js.components

import polyhedra.common.*
import polyhedra.js.params.*
import react.*

fun <T : Tagged> RBuilder.pDropdown(param: EnumParam<T>, disabled: Boolean = false) {
    child<PValueComponentProps<EnumParam<T>>, PDropdown<T>> {
        attrs {
            this.param = param
            this.disabled = disabled
        }
    }
}

class PDropdown<T : Tagged>(props: PValueComponentProps<EnumParam<T>>) : PValueComponent<T, EnumParam<T>, PValueComponentProps<EnumParam<T>>, PValueComponentState<T>>(props) {
    override fun RBuilder.render() {
        dropdown<T> {
            disabled = props.disabled
            value = props.param.value
            options = props.param.options
            onChange = { props.param.updateValue(it) }
        }
    }
}