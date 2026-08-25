/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.main

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.*
import polyhedra.web.poly.IndicatorMessage

@Composable
fun <T> MessageSpan(message: IndicatorMessage<T>) {
    Span(attrs = { classes(*message.indicator.classes.split(' ').toTypedArray()) }) {
        Text(message.indicator.text)
    }
    Aside(attrs = { classes("tooltip-text") }) {
        Text(message.indicator.tooltip.replace("{}", message.value.toString()))
    }
}

@Composable
fun GroupHeader(text: String) {
    Div(attrs = { classes("text-row") }) {
        Div(attrs = { classes("header") }) { Text(text) }
    }
}

@Composable
fun TableBody(content: @Composable () -> Unit) {
    Table { Tbody { content() } }
}

@Composable
fun ControlRow(
    label: String,
    labelColumnSpan: Int = 1,
    content: @Composable () -> Unit,
) {
    require(labelColumnSpan >= 1)
    Tr(attrs = { classes("control") }) {
        Td(attrs = {
            if (labelColumnSpan > 1) attr("colspan", labelColumnSpan.toString())
        }) { Text(label) }
        Td { content() }
    }
}

@Composable
fun ControlRow2(
    label: String,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    Tr(attrs = { classes("control") }) {
        Td { Text(label) }
        Td { first() }
        Td { second() }
    }
}
