/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.main

import polyhedra.js.poly.*
import react.*
import react.dom.*

fun <T> RBuilder.messageSpan(msg: IndicatorMessage<T>) {
    span {
        span("emoji") { +msg.indicator.text }
        span("tooltip-text") {
            +msg.indicator.tooltip.replace("{}", msg.value.toString())
        }
    }
}