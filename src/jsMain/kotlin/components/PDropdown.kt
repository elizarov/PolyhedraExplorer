/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.components

import polyhedra.common.util.*
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