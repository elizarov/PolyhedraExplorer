/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.components

import kotlinx.html.*
import kotlinx.html.js.*
import polyhedra.js.main.*
import polyhedra.js.params.*
import react.*
import react.dom.*

fun RBuilder.pCheckbox(param: BooleanParam, disabled: Boolean = false) {
    child(PCheckbox::class) {
        attrs {
            this.params = param
            this.disabled = disabled
        }
    }
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class PCheckbox(props: PValueComponentProps<BooleanParam>) : PValueComponent<Boolean, BooleanParam, PValueComponentProps<BooleanParam>, PValueComponentState<Boolean>>(props) {
    override fun RBuilder.render() {
        div("checkbox") {
            input(InputType.checkBox) {
                attrs {
                    disabled = props.disabled
                    // See https://github.com/JetBrains/kotlin-wrappers/issues/35
                    this["checked"] = state.value
                    onChangeFunction = {
                        props.params.toggle()
                    }
                }
            }
            span("checkmark") {
                onClick { props.params.toggle() }
            }
        }
    }
}