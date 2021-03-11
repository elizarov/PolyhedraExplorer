/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.util

import kotlinx.browser.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import kotlin.experimental.*

fun HTMLElement.computedStyle() =
    window.document.defaultView!!.getComputedStyle(this)

fun MouseEvent.isLeftButtonPressed() =
    (buttons and 1) != 0.toShort()