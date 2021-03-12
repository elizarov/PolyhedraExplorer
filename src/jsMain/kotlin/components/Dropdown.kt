/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.components

import kotlinx.html.js.*
import org.w3c.dom.*
import react.*
import react.dom.*

external interface DropdownProps<T> : RProps {
    var disabled: Boolean
    var value: T
    var options: List<T>
    var onChange: (T) -> Unit
}

fun <T> RBuilder.dropdown(handler: DropdownProps<T>.() -> Unit) {
    child<DropdownProps<T>, Dropdown<T>> {
        attrs {
            disabled = false
            handler()
        }
    }
}

class Dropdown<T>(props: DropdownProps<T>) : RComponent<DropdownProps<T>, RState>(props) {
    override fun RBuilder.render() {
        select {
            attrs {
                disabled = props.disabled
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