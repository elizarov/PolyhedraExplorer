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
            this.params = param
            this.disabled = disabled
        }
    }
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class PDropdown<T : Tagged>(props: PValueComponentProps<EnumParam<T>>) : PValueComponent<T, EnumParam<T>, PValueComponentProps<EnumParam<T>>, PValueComponentState<T>>(props) {
    override fun RBuilder.render() {
        dropdown<T> {
            disabled = props.disabled
            value = props.params.value
            options = props.params.options
            onChange = { props.params.updateValue(it) }
        }
    }
}